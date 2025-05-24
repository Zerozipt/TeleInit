package com.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.entity.dto.CacheWarmupTask;
import com.example.mapper.CacheWarmupTaskMapper;
import com.example.service.CacheWarmupService;
import com.example.service.SmartCacheService;
import com.example.service.GroupCacheService;
import com.example.service.ChatCacheService;
import com.example.utils.RedisKeys;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.function.Supplier;

@Service
public class CacheWarmupServiceImpl implements CacheWarmupService {
    
    private static final Logger logger = LoggerFactory.getLogger(CacheWarmupServiceImpl.class);
    private static final int MAX_RETRY_COUNT = 3;
    
    @Resource
    private CacheWarmupTaskMapper warmupTaskMapper;
    
    @Autowired
    private SmartCacheService smartCacheService;
    
    @Autowired 
    private GroupCacheService groupCacheService;
    
    @Autowired
    private ChatCacheService chatCacheService;
    
    @Override
    @Transactional
    public CacheWarmupTask createWarmupTask(String cacheType, String cacheKey, String entityId, int priority) {
        try {
            // 检查是否已存在相同的待处理任务
            CacheWarmupTask existingTask = warmupTaskMapper.findByCacheKey(cacheKey);
            if (existingTask != null) {
                logger.debug("预热任务已存在，跳过创建: cacheKey={}", cacheKey);
                return null;
            }
            
            // 创建新的预热任务
            CacheWarmupTask task = CacheWarmupTask.create(cacheType, cacheKey, entityId, priority);
            warmupTaskMapper.insert(task);
            
            logger.info("创建缓存预热任务: cacheType={}, cacheKey={}, entityId={}, priority={}", 
                       cacheType, cacheKey, entityId, priority);
            return task;
            
        } catch (Exception e) {
            logger.error("创建预热任务失败: cacheType={}, cacheKey={}", cacheType, cacheKey, e);
            return null;
        }
    }
    
    @Override
    public int processPendingTasks(int limit) {
        List<CacheWarmupTask> pendingTasks = warmupTaskMapper.findPendingTasks(limit);
        int processedCount = 0;
        
        for (CacheWarmupTask task : pendingTasks) {
            if (processWarmupTask(task)) {
                processedCount++;
            }
        }
        
        if (processedCount > 0) {
            logger.info("处理预热任务完成: 处理数量={}/{}", processedCount, pendingTasks.size());
        }
        
        return processedCount;
    }
    
    @Override
    public int retryFailedTasks(int maxRetryCount, int limit) {
        List<CacheWarmupTask> retryableTasks = warmupTaskMapper.findRetryableTasks(maxRetryCount, limit);
        int processedCount = 0;
        
        for (CacheWarmupTask task : retryableTasks) {
            logger.info("重试失败任务: cacheKey={}, retryCount={}", task.getCacheKey(), task.getRetryCount());
            if (processWarmupTask(task)) {
                processedCount++;
            }
        }
        
        if (processedCount > 0) {
            logger.info("重试失败任务完成: 处理数量={}/{}", processedCount, retryableTasks.size());
        }
        
        return processedCount;
    }
    
    @Override
    public int cleanupOldTasks(int days) {
        try {
            int deletedCount = warmupTaskMapper.cleanupCompletedTasks(days);
            if (deletedCount > 0) {
                logger.info("清理旧预热任务: 删除数量={}, 保留天数={}", deletedCount, days);
            }
            return deletedCount;
        } catch (Exception e) {
            logger.error("清理旧预热任务失败", e);
            return 0;
        }
    }
    
    @Override
    public WarmupTaskStats getTaskStats() {
        try {
            WarmupTaskStats stats = new WarmupTaskStats();
            
            // 统计各状态的任务数量
            QueryWrapper<CacheWarmupTask> query = new QueryWrapper<>();
            query.eq("status", "PENDING");
            stats.setPendingCount(Math.toIntExact(warmupTaskMapper.selectCount(query)));
            
            query = new QueryWrapper<>();
            query.eq("status", "PROCESSING");
            stats.setProcessingCount(Math.toIntExact(warmupTaskMapper.selectCount(query)));
            
            query = new QueryWrapper<>();
            query.eq("status", "COMPLETED");
            stats.setCompletedCount(Math.toIntExact(warmupTaskMapper.selectCount(query)));
            
            query = new QueryWrapper<>();
            query.eq("status", "FAILED");
            stats.setFailedCount(Math.toIntExact(warmupTaskMapper.selectCount(query)));
            
            // 统计最近24小时的任务数量
            stats.setRecentTasksCount(warmupTaskMapper.countRecentTasks(24));
            
            return stats;
        } catch (Exception e) {
            logger.error("获取预热任务统计失败", e);
            return new WarmupTaskStats();
        }
    }
    
    /**
     * 处理单个预热任务
     */
    private boolean processWarmupTask(CacheWarmupTask task) {
        try {
            // 标记为处理中
            task.markAsProcessing();
            warmupTaskMapper.updateById(task);
            
            // 根据缓存类型执行不同的预热逻辑
            boolean success = executeWarmupTask(task);
            
            if (success) {
                // 标记为完成
                task.markAsCompleted();
                logger.debug("预热任务执行成功: cacheKey={}", task.getCacheKey());
            } else {
                // 标记为失败
                task.markAsFailed();
                logger.warn("预热任务执行失败: cacheKey={}, retryCount={}", 
                           task.getCacheKey(), task.getRetryCount());
            }
            
            warmupTaskMapper.updateById(task);
            return success;
            
        } catch (Exception e) {
            logger.error("处理预热任务异常: cacheKey={}", task.getCacheKey(), e);
            
            // 标记为失败
            task.markAsFailed();
            warmupTaskMapper.updateById(task);
            return false;
        }
    }
    
    /**
     * 执行具体的预热逻辑
     */
    private boolean executeWarmupTask(CacheWarmupTask task) {
        String cacheType = task.getCacheType();
        String entityId = task.getEntityId();
        String cacheKey = task.getCacheKey();
        
        try {
            switch (cacheType) {
                case CacheWarmupTask.CacheTypes.USER_GROUPS:
                    return warmupUserGroups(entityId, cacheKey);
                    
                case CacheWarmupTask.CacheTypes.GROUP_DETAIL:
                    return warmupGroupDetail(entityId, cacheKey);
                    
                case CacheWarmupTask.CacheTypes.CHAT_HISTORY:
                    return warmupChatHistory(entityId, cacheKey);
                    
                default:
                    logger.warn("未知的缓存类型: {}", cacheType);
                    return false;
            }
        } catch (Exception e) {
            logger.error("执行预热任务失败: cacheType={}, entityId={}", cacheType, entityId, e);
            return false;
        }
    }
    
    /**
     * 预热用户群组列表
     */
    private boolean warmupUserGroups(String entityId, String cacheKey) {
        try {
            int userId = Integer.parseInt(entityId);
            // 通过GroupCacheService预热用户群组
            groupCacheService.getUserGroups(userId);
            logger.debug("用户群组预热完成: userId={}", userId);
            return true;
        } catch (Exception e) {
            logger.error("用户群组预热失败: entityId={}", entityId, e);
            return false;
        }
    }
    
    /**
     * 预热群组详情
     */
    private boolean warmupGroupDetail(String entityId, String cacheKey) {
        try {
            // 通过GroupCacheService预热群组详情
            groupCacheService.getGroupDetail(entityId);
            logger.debug("群组详情预热完成: groupId={}", entityId);
            return true;
        } catch (Exception e) {
            logger.error("群组详情预热失败: entityId={}", entityId, e);
            return false;
        }
    }
    
    /**
     * 预热聊天历史
     */
    private boolean warmupChatHistory(String entityId, String cacheKey) {
        try {
            // 根据缓存键判断是群组还是私聊
            if (cacheKey.contains(RedisKeys.CHAT_GROUP)) {
                // 群组聊天历史
                chatCacheService.getGroupChatHistory(entityId, 50);
                logger.debug("群组聊天历史预热完成: groupId={}", entityId);
            } else if (cacheKey.contains(RedisKeys.CHAT_PRIVATE)) {
                // 私聊历史 - 修复方法调用
                int userId = Integer.parseInt(entityId);
                chatCacheService.getPrivateChatHistory(userId, 50);
                logger.debug("私聊历史预热完成: userId={}", userId);
            } else {
                logger.warn("未知的聊天历史缓存类型: cacheKey={}", cacheKey);
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.error("聊天历史预热失败: entityId={}, cacheKey={}", entityId, cacheKey, e);
            return false;
        }
    }
    
    // ========== 定时任务 ==========
    
    /**
     * 定时处理待处理的预热任务
     * 每10秒执行一次
     */
    @Scheduled(fixedDelay = 10000)
    public void processWarmupTasksScheduled() {
        try {
            int processed = processPendingTasks(20);
            if (processed > 0) {
                logger.debug("定时预热任务处理完成: 处理数量={}", processed);
            }
        } catch (Exception e) {
            logger.error("定时预热任务处理失败", e);
        }
    }
    
    /**
     * 定时重试失败的预热任务
     * 每1分钟执行一次
     */
    @Scheduled(fixedDelay = 60000)
    public void retryFailedTasksScheduled() {
        try {
            int processed = retryFailedTasks(MAX_RETRY_COUNT, 10);
            if (processed > 0) {
                logger.info("定时重试失败预热任务完成: 处理数量={}", processed);
            }
        } catch (Exception e) {
            logger.error("定时重试失败预热任务失败", e);
        }
    }
    
    /**
     * 定时清理旧的预热任务
     * 每天凌晨3点执行
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldTasksScheduled() {
        try {
            int deleted = cleanupOldTasks(7); // 保留7天
            if (deleted > 0) {
                logger.info("定时清理旧预热任务完成: 删除数量={}", deleted);
            }
        } catch (Exception e) {
            logger.error("定时清理旧预热任务失败", e);
        }
    }
    
    /**
     * 定时输出预热任务统计
     * 每10分钟执行一次
     */
    @Scheduled(fixedDelay = 600000)
    public void logTaskStatsScheduled() {
        try {
            WarmupTaskStats stats = getTaskStats();
            if (stats.getPendingCount() > 0 || stats.getProcessingCount() > 0 || 
                stats.getFailedCount() > 0 || stats.getRecentTasksCount() > 0) {
                logger.info("预热任务统计: {}", stats);
            }
        } catch (Exception e) {
            logger.error("输出预热任务统计失败", e);
        }
    }
} 