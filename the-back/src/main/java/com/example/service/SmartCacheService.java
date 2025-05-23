package com.example.service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 智能缓存服务接口 - P2级别优化
 */
public interface SmartCacheService {
    
    /**
     * 智能缓存失效和预热
     * @param cacheKey 缓存键
     * @param dataLoader 数据加载器
     */
    void smartInvalidateAndWarmup(String cacheKey, Supplier<Object> dataLoader);
    
    /**
     * 带分布式锁和布隆过滤器的缓存读取
     * @param cacheKey 缓存键
     * @param dbQuery 数据库查询函数
     * @param clazz 返回类型
     * @param ttl 缓存过期时间
     * @return 缓存数据
     */
    <T> T getWithProtection(String cacheKey, Supplier<T> dbQuery, Class<T> clazz, Duration ttl);
    
    /**
     * 异步预热缓存
     * @param cacheKey 缓存键
     * @param dataLoader 数据加载器
     * @param ttl 缓存过期时间
     */
    void asyncWarmupCache(String cacheKey, Supplier<Object> dataLoader, Duration ttl);
    
    /**
     * 检查缓存键是否可能存在（布隆过滤器）
     * @param cacheKey 缓存键
     * @return 是否可能存在
     */
    boolean mightExist(String cacheKey);
    
    /**
     * 添加缓存键到布隆过滤器
     * @param cacheKey 缓存键
     */
    void addToFilter(String cacheKey);
} 