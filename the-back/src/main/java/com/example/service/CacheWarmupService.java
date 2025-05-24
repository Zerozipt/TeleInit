package com.example.service;

import com.example.entity.dto.CacheWarmupTask;

/**
 * 缓存预热服务接口
 */
public interface CacheWarmupService {
    
    /**
     * 创建缓存预热任务
     * @param cacheType 缓存类型
     * @param cacheKey 缓存键
     * @param entityId 实体ID
     * @param priority 优先级
     * @return 创建的任务，如果已存在则返回null
     */
    CacheWarmupTask createWarmupTask(String cacheType, String cacheKey, String entityId, int priority);
    
    /**
     * 处理待处理的预热任务
     * @param limit 处理数量限制
     * @return 实际处理的任务数量
     */
    int processPendingTasks(int limit);
    
    /**
     * 重试失败的任务
     * @param maxRetryCount 最大重试次数
     * @param limit 处理数量限制
     * @return 实际处理的任务数量
     */
    int retryFailedTasks(int maxRetryCount, int limit);
    
    /**
     * 清理已完成的旧任务
     * @param days 保留天数
     * @return 删除的任务数量
     */
    int cleanupOldTasks(int days);
    
    /**
     * 获取任务统计信息
     * @return 统计信息
     */
    WarmupTaskStats getTaskStats();
    
    /**
     * 任务统计信息
     */
    class WarmupTaskStats {
        private int pendingCount;
        private int processingCount;
        private int completedCount;
        private int failedCount;
        private int recentTasksCount;
        
        // getters and setters
        public int getPendingCount() { return pendingCount; }
        public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }
        
        public int getProcessingCount() { return processingCount; }
        public void setProcessingCount(int processingCount) { this.processingCount = processingCount; }
        
        public int getCompletedCount() { return completedCount; }
        public void setCompletedCount(int completedCount) { this.completedCount = completedCount; }
        
        public int getFailedCount() { return failedCount; }
        public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
        
        public int getRecentTasksCount() { return recentTasksCount; }
        public void setRecentTasksCount(int recentTasksCount) { this.recentTasksCount = recentTasksCount; }
        
        @Override
        public String toString() {
            return String.format("WarmupTaskStats{pending=%d, processing=%d, completed=%d, failed=%d, recent=%d}", 
                               pendingCount, processingCount, completedCount, failedCount, recentTasksCount);
        }
    }
} 