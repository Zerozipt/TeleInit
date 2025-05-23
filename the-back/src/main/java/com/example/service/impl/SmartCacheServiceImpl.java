package com.example.service.impl;

import com.example.service.SmartCacheService;
import com.example.service.RedisService;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 智能缓存服务实现 - P2级别优化
 */
@Service
public class SmartCacheServiceImpl implements SmartCacheService {
    
    private static final Logger logger = LoggerFactory.getLogger(SmartCacheServiceImpl.class);
    private static final String CACHE_LOCK_PREFIX = "cache_lock:";
    private static final Duration LOCK_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);
    
    @Autowired
    private RedisService redisService;
    
    // 简单的内存布隆过滤器模拟（生产环境建议使用Guava BloomFilter或Redis BloomFilter）
    private final ConcurrentHashMap<String, Boolean> bloomFilter = new ConcurrentHashMap<>();
    
    @Override
    public void smartInvalidateAndWarmup(String cacheKey, Supplier<Object> dataLoader) {
        try {
            // 步骤1：立即删除旧缓存
            redisService.delete(cacheKey);
            logger.debug("缓存已删除: {}", cacheKey);
            
            // 步骤2：异步预热新缓存
            asyncWarmupCache(cacheKey, dataLoader, DEFAULT_TTL);
            
        } catch (Exception e) {
            logger.error("智能缓存失效失败: {}", cacheKey, e);
        }
    }
    
    @Override
    public <T> T getWithProtection(String cacheKey, Supplier<T> dbQuery, Class<T> clazz, Duration ttl) {
        try {
            // 步骤1：先查缓存
            String cached = redisService.get(cacheKey);
            if (cached != null && !"NULL".equals(cached)) {
                T result = JSON.parseObject(cached, clazz);
                logger.debug("缓存命中: {}", cacheKey);
                return result;
            }
            
            // 步骤2：布隆过滤器检查（防止缓存穿透）
            if (!mightExist(cacheKey)) {
                logger.debug("布隆过滤器判断数据不存在: {}", cacheKey);
                return null;
            }
            
            // 步骤3：分布式锁保护数据库查询
            String lockKey = CACHE_LOCK_PREFIX + cacheKey;
            return executeWithLock(lockKey, () -> {
                // 双重检查
                String recheck = redisService.get(cacheKey);
                if (recheck != null && !"NULL".equals(recheck)) {
                    return JSON.parseObject(recheck, clazz);
                }
                
                // 查询数据库
                T data = dbQuery.get();
                if (data != null) {
                    // 缓存数据并添加到布隆过滤器
                    redisService.set(cacheKey, JSON.toJSONString(data), ttl);
                    addToFilter(cacheKey);
                    logger.debug("数据已缓存: {}", cacheKey);
                } else {
                    // 缓存空值，防止穿透
                    redisService.set(cacheKey, "NULL", Duration.ofMinutes(5));
                    logger.debug("缓存空值: {}", cacheKey);
                }
                return data;
            });
            
        } catch (Exception e) {
            logger.error("保护性缓存读取失败: {}", cacheKey, e);
            // 降级到直接查询数据库
            return dbQuery.get();
        }
    }
    
    @Override
    @Async
    public void asyncWarmupCache(String cacheKey, Supplier<Object> dataLoader, Duration ttl) {
        String lockKey = CACHE_LOCK_PREFIX + cacheKey;
        
        try {
            // 尝试获取分布式锁
            boolean acquired = acquireLock(lockKey);
            if (!acquired) {
                logger.debug("其他实例正在预热缓存: {}", cacheKey);
                return;
            }
            
            // 双重检查：可能其他实例已经预热了
            if (redisService.exists(cacheKey)) {
                logger.debug("缓存已被其他实例预热: {}", cacheKey);
                return;
            }
            
            // 加载数据并缓存
            Object data = dataLoader.get();
            if (data != null) {
                redisService.set(cacheKey, JSON.toJSONString(data), ttl);
                addToFilter(cacheKey);
                logger.info("缓存预热完成: {}", cacheKey);
            }
            
        } catch (Exception e) {
            logger.error("缓存预热失败: {}", cacheKey, e);
        } finally {
            // 释放锁
            releaseLock(lockKey);
        }
    }
    
    @Override
    public boolean mightExist(String cacheKey) {
        // 🔧 HOTFIX: 放宽布隆过滤器检查，解决群组访问失败问题
        // 对于群组相关的缓存键，总是允许查询数据库
        if (cacheKey.contains("group") || cacheKey.contains("GROUP")) {
            return true; // 群组相关缓存总是允许查询
        }
        
        // 简单的内存布隆过滤器实现
        // 生产环境建议使用更高效的实现
        return bloomFilter.containsKey(cacheKey) || 
               cacheKey.contains("system") || 
               cacheKey.contains("default") ||
               cacheKey.contains("user"); // 也放宽用户相关检查
    }
    
    @Override
    public void addToFilter(String cacheKey) {
        bloomFilter.put(cacheKey, true);
        logger.debug("已添加到布隆过滤器: {}", cacheKey);
    }
    
    /**
     * 使用分布式锁执行操作
     */
    private <T> T executeWithLock(String lockKey, Supplier<T> action) {
        boolean acquired = false;
        try {
            acquired = acquireLock(lockKey);
            if (acquired) {
                return action.get();
            } else {
                // 等待其他线程完成
                Thread.sleep(100);
                return action.get(); // 简化处理，可以递归重试
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("操作被中断", e);
        } finally {
            if (acquired) {
                releaseLock(lockKey);
            }
        }
    }
    
    /**
     * 获取分布式锁
     */
    private boolean acquireLock(String lockKey) {
        try {
            // 使用Redis实现分布式锁
            String lockValue = String.valueOf(System.currentTimeMillis());
            // 简化实现，生产环境建议使用Redisson等专业库
            String existing = redisService.get(lockKey);
            if (existing == null) {
                redisService.set(lockKey, lockValue, LOCK_TIMEOUT);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("获取分布式锁失败: {}", lockKey, e);
            return false;
        }
    }
    
    /**
     * 释放分布式锁
     */
    private void releaseLock(String lockKey) {
        try {
            redisService.delete(lockKey);
            logger.debug("分布式锁已释放: {}", lockKey);
        } catch (Exception e) {
            logger.error("释放分布式锁失败: {}", lockKey, e);
        }
    }
} 