package com.example.entity.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Date;

/**
 * 缓存预热任务实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("cache_warmup_tasks")
public class CacheWarmupTask {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("cache_key")
    private String cacheKey;
    
    @TableField("cache_type")
    private String cacheType;
    
    @TableField("entity_id")
    private String entityId;
    
    @TableField("priority")
    private Integer priority;
    
    @TableField("status")
    private TaskStatus status;
    
    @TableField("retry_count")
    private Integer retryCount;
    
    @TableField("created_at")
    private Date createdAt;
    
    @TableField("processed_at")
    private Date processedAt;
    
    /**
     * 任务状态枚举
     */
    public enum TaskStatus {
        PENDING,    // 待处理
        PROCESSING, // 处理中
        COMPLETED,  // 已完成
        FAILED      // 失败
    }
    
    /**
     * 缓存类型常量
     */
    public static class CacheTypes {
        public static final String USER_GROUPS = "USER_GROUPS";
        public static final String GROUP_DETAIL = "GROUP_DETAIL";
        public static final String CHAT_HISTORY = "CHAT_HISTORY";
        public static final String USER_FRIENDS = "USER_FRIENDS";
        public static final String ONLINE_USERS = "ONLINE_USERS";
    }
    
    /**
     * 优先级常量
     */
    public static class Priority {
        public static final int HIGHEST = 1;    // 最高优先级
        public static final int HIGH = 3;       // 高优先级  
        public static final int MEDIUM = 5;     // 中等优先级
        public static final int LOW = 7;        // 低优先级
        public static final int LOWEST = 10;    // 最低优先级
    }
    
    /**
     * 便捷构造方法
     */
    public static CacheWarmupTask create(String cacheType, String cacheKey, String entityId, int priority) {
        CacheWarmupTask task = new CacheWarmupTask();
        task.setCacheType(cacheType);
        task.setCacheKey(cacheKey);
        task.setEntityId(entityId);
        task.setPriority(priority);
        task.setStatus(TaskStatus.PENDING);
        task.setRetryCount(0);
        task.setCreatedAt(new Date());
        return task;
    }
    
    /**
     * 标记为处理中
     */
    public void markAsProcessing() {
        this.status = TaskStatus.PROCESSING;
        this.processedAt = new Date();
    }
    
    /**
     * 标记为完成
     */
    public void markAsCompleted() {
        this.status = TaskStatus.COMPLETED;
        this.processedAt = new Date();
    }
    
    /**
     * 标记为失败
     */
    public void markAsFailed() {
        this.status = TaskStatus.FAILED;
        this.processedAt = new Date();
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }
} 