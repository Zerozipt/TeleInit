package com.example.service;

import com.example.entity.dto.OutboxEvent;
import java.util.List;

/**
 * 本地消息表服务接口
 */
public interface OutboxEventService {
    
    /**
     * 创建事件
     * @param eventType 事件类型
     * @param entityId 实体ID
     * @param payload 事件载荷
     * @return 创建的事件
     */
    OutboxEvent createEvent(String eventType, String entityId, String payload);
    
    /**
     * 处理待处理的事件
     * @param limit 处理数量限制
     * @return 处理的事件数量
     */
    int processPendingEvents(int limit);
    
    /**
     * 重试失败的事件
     * @param maxRetryCount 最大重试次数
     * @param limit 处理数量限制
     * @return 重试的事件数量
     */
    int retryFailedEvents(int maxRetryCount, int limit);
    
    /**
     * 标记事件为成功
     * @param eventId 事件ID
     */
    void markEventAsSuccess(Long eventId);
    
    /**
     * 标记事件为失败
     * @param eventId 事件ID
     * @param errorMessage 错误信息
     */
    void markEventAsFailed(Long eventId, String errorMessage);
    
    /**
     * 根据实体ID和事件类型查询事件
     * @param entityId 实体ID
     * @param eventType 事件类型
     * @return 事件列表
     */
    List<OutboxEvent> findEventsByEntityAndType(String entityId, String eventType);
} 