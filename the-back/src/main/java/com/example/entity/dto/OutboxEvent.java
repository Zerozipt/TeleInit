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
 * 本地消息表实体，用于实现最终一致性
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("outbox_events")
public class OutboxEvent {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("event_type")
    private String eventType;
    
    @TableField("entity_id")
    private String entityId;
    
    @TableField("payload")
    private String payload; // JSON格式的事件数据
    
    @TableField("status")
    private EventStatus status;
    
    @TableField("retry_count")
    private Integer retryCount;
    
    @TableField("created_at")
    private Date createdAt;
    
    @TableField("processed_at")
    private Date processedAt;
    
    @TableField("error_message")
    private String errorMessage;
    
    /**
     * 事件状态枚举
     */
    public enum EventStatus {
        PENDING,    // 待处理
        PROCESSING, // 处理中
        SENT,       // 已发送
        FAILED      // 失败
    }
    
    /**
     * 事件类型常量
     */
    public static class EventTypes {
        public static final String GROUP_CREATED = "GROUP_CREATED";
        public static final String GROUP_MEMBER_ADDED = "GROUP_MEMBER_ADDED";
        public static final String GROUP_MEMBER_REMOVED = "GROUP_MEMBER_REMOVED";
        public static final String FRIEND_REQUEST_SENT = "FRIEND_REQUEST_SENT";
        public static final String FRIEND_REQUEST_ACCEPTED = "FRIEND_REQUEST_ACCEPTED";
        public static final String FRIEND_REQUEST_REJECTED = "FRIEND_REQUEST_REJECTED";
        public static final String CACHE_INVALIDATION = "CACHE_INVALIDATION";
        public static final String MESSAGE_SAVED = "MESSAGE_SAVED";
    }
    
    /**
     * 便捷构造方法
     */
    public static OutboxEvent create(String eventType, String entityId, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.setEventType(eventType);
        event.setEntityId(entityId);
        event.setPayload(payload);
        event.setStatus(EventStatus.PENDING);
        event.setRetryCount(0);
        event.setCreatedAt(new Date());
        return event;
    }
    
    /**
     * 增加重试次数
     */
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }
    
    /**
     * 标记为处理中
     */
    public void markAsProcessing() {
        this.status = EventStatus.PROCESSING;
        this.processedAt = new Date();
    }
    
    /**
     * 标记为成功
     */
    public void markAsSuccess() {
        this.status = EventStatus.SENT;
        this.processedAt = new Date();
    }
    
    /**
     * 标记为失败
     */
    public void markAsFailed(String errorMessage) {
        this.status = EventStatus.FAILED;
        this.errorMessage = errorMessage;
        this.processedAt = new Date();
    }
} 