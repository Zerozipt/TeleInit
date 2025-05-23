package com.example.service.impl;

import com.example.entity.dto.OutboxEvent;
import com.example.mapper.OutboxEventMapper;
import com.example.service.OutboxEventService;
import com.example.service.GroupCacheService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 本地消息表服务实现
 */
@Service
public class OutboxEventServiceImpl implements OutboxEventService {
    
    private static final Logger logger = LoggerFactory.getLogger(OutboxEventServiceImpl.class);
    
    @Resource
    private OutboxEventMapper outboxEventMapper;
    
    @Autowired
    private GroupCacheService groupCacheService;
    
    @Override
    @Transactional
    public OutboxEvent createEvent(String eventType, String entityId, String payload) {
        OutboxEvent event = OutboxEvent.create(eventType, entityId, payload);
        outboxEventMapper.insert(event);
        logger.debug("创建事件: eventType={}, entityId={}, eventId={}", eventType, entityId, event.getId());
        return event;
    }
    
    @Override
    public int processPendingEvents(int limit) {
        List<OutboxEvent> pendingEvents = outboxEventMapper.findPendingEvents(limit);
        int processedCount = 0;
        
        for (OutboxEvent event : pendingEvents) {
            try {
                // 标记为处理中
                event.markAsProcessing();
                outboxEventMapper.updateById(event);
                
                // 根据事件类型处理
                processEvent(event);
                
                // 标记为成功
                event.markAsSuccess();
                outboxEventMapper.updateById(event);
                processedCount++;
                
                logger.debug("事件处理成功: eventId={}, eventType={}", event.getId(), event.getEventType());
                
            } catch (Exception e) {
                logger.error("事件处理失败: eventId={}, eventType={}", event.getId(), event.getEventType(), e);
                
                // 标记为失败
                event.markAsFailed(e.getMessage());
                event.incrementRetryCount();
                outboxEventMapper.updateById(event);
            }
        }
        
        return processedCount;
    }
    
    @Override
    public int retryFailedEvents(int maxRetryCount, int limit) {
        List<OutboxEvent> retryableEvents = outboxEventMapper.findRetryableEvents(maxRetryCount, limit);
        int retryCount = 0;
        
        for (OutboxEvent event : retryableEvents) {
            try {
                // 重置状态为处理中
                event.setStatus(OutboxEvent.EventStatus.PROCESSING);
                outboxEventMapper.updateById(event);
                
                // 重新处理事件
                processEvent(event);
                
                // 标记为成功
                event.markAsSuccess();
                outboxEventMapper.updateById(event);
                retryCount++;
                
                logger.info("事件重试成功: eventId={}, retryCount={}", event.getId(), event.getRetryCount());
                
            } catch (Exception e) {
                logger.error("事件重试失败: eventId={}, retryCount={}", event.getId(), event.getRetryCount(), e);
                
                // 增加重试次数并标记为失败
                event.incrementRetryCount();
                if (event.getRetryCount() >= maxRetryCount) {
                    event.markAsFailed("超过最大重试次数: " + e.getMessage());
                } else {
                    event.setStatus(OutboxEvent.EventStatus.FAILED);
                }
                outboxEventMapper.updateById(event);
            }
        }
        
        return retryCount;
    }
    
    @Override
    public void markEventAsSuccess(Long eventId) {
        OutboxEvent event = outboxEventMapper.selectById(eventId);
        if (event != null) {
            event.markAsSuccess();
            outboxEventMapper.updateById(event);
        }
    }
    
    @Override
    public void markEventAsFailed(Long eventId, String errorMessage) {
        OutboxEvent event = outboxEventMapper.selectById(eventId);
        if (event != null) {
            event.markAsFailed(errorMessage);
            event.incrementRetryCount();
            outboxEventMapper.updateById(event);
        }
    }
    
    @Override
    public List<OutboxEvent> findEventsByEntityAndType(String entityId, String eventType) {
        return outboxEventMapper.findByEntityIdAndEventType(entityId, eventType);
    }
    
    /**
     * 根据事件类型处理具体事件
     */
    private void processEvent(OutboxEvent event) {
        String eventType = event.getEventType();
        String payload = event.getPayload();
        
        logger.debug("处理事件: eventType={}, payload={}", eventType, payload);
        
        try {
            JSONObject data = JSON.parseObject(payload);
            
            switch (eventType) {
                case OutboxEvent.EventTypes.GROUP_CREATED:
                case OutboxEvent.EventTypes.GROUP_MEMBER_ADDED:
                case OutboxEvent.EventTypes.GROUP_MEMBER_REMOVED:
                    handleGroupEvent(data);
                    break;
                    
                case OutboxEvent.EventTypes.CACHE_INVALIDATION:
                    handleCacheInvalidation(data);
                    break;
                    
                case OutboxEvent.EventTypes.FRIEND_REQUEST_SENT:
                case OutboxEvent.EventTypes.FRIEND_REQUEST_ACCEPTED:
                case OutboxEvent.EventTypes.FRIEND_REQUEST_REJECTED:
                    handleFriendEvent(data);
                    break;
                    
                default:
                    logger.warn("未知的事件类型: {}", eventType);
            }
            
        } catch (Exception e) {
            logger.error("事件处理异常: eventType={}, payload={}", eventType, payload, e);
            throw e;
        }
    }
    
    /**
     * 处理群组相关事件
     */
    private void handleGroupEvent(JSONObject data) {
        String groupId = data.getString("groupId");
        Integer userId = data.getInteger("userId");
        
        if (groupId != null) {
            // 失效群组详情缓存
            groupCacheService.invalidateGroupDetail(groupId);
            logger.debug("群组缓存失效: groupId={}", groupId);
        }
        
        if (userId != null) {
            // 失效用户群组列表缓存
            groupCacheService.invalidateUserGroups(userId);
            logger.debug("用户群组缓存失效: userId={}", userId);
        }
    }
    
    /**
     * 处理缓存失效事件
     */
    private void handleCacheInvalidation(JSONObject data) {
        String cacheType = data.getString("cacheType");
        String cacheKey = data.getString("cacheKey");
        
        // 根据缓存类型进行不同的处理
        if ("USER_GROUPS".equals(cacheType)) {
            Integer userId = data.getInteger("userId");
            if (userId != null) {
                groupCacheService.invalidateUserGroups(userId);
            }
        } else if ("GROUP_DETAIL".equals(cacheType)) {
            String groupId = data.getString("groupId");
            if (groupId != null) {
                groupCacheService.invalidateGroupDetail(groupId);
            }
        }
        
        logger.debug("缓存失效处理完成: cacheType={}, cacheKey={}", cacheType, cacheKey);
    }
    
    /**
     * 处理好友相关事件
     */
    private void handleFriendEvent(JSONObject data) {
        // 好友事件可能需要清理相关缓存或发送通知
        // 这里可以根据具体需求扩展
        logger.debug("处理好友事件: {}", data.toJSONString());
    }
} 