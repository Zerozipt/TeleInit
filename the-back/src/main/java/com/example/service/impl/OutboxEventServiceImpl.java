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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

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
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
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
                case OutboxEvent.EventTypes.GROUP_NAME_CHANGED:
                case OutboxEvent.EventTypes.GROUP_DISSOLVED:
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
        Integer memberId = data.getInteger("memberId");
        String action = data.getString("action");
        
        // 失效缓存
        if (groupId != null) {
            groupCacheService.invalidateGroupDetail(groupId);
            logger.debug("群组缓存失效: groupId={}", groupId);
        }
        
        if (userId != null) {
            groupCacheService.invalidateUserGroups(userId);
            logger.debug("用户群组缓存失效: userId={}", userId);
        }
        
        if (memberId != null) {
            groupCacheService.invalidateUserGroups(memberId);
            logger.debug("成员群组缓存失效: memberId={}", memberId);
        }
        
        // 发送WebSocket通知
        try {
            switch (action) {
                case "GROUP_MEMBER_REMOVED_BY_ADMIN":
                    sendMemberRemovedNotification(data);
                    break;
                case "GROUP_NAME_CHANGED":
                    sendGroupNameChangedNotification(data);
                    break;
                case "GROUP_DISSOLVED":
                    sendGroupDissolvedNotification(data);
                    break;
                default:
                    logger.debug("未处理的群组事件: action={}", action);
            }
        } catch (Exception e) {
            logger.error("发送WebSocket通知失败: action={}, data={}", action, data.toJSONString(), e);
        }
    }
    
    /**
     * 发送成员被移除通知
     */
    private void sendMemberRemovedNotification(JSONObject data) {
        String groupId = data.getString("groupId");
        Integer memberId = data.getInteger("memberId");
        
        if (groupId != null && memberId != null) {
            // 通知被移除的用户
            Map<String, Object> notification = Map.of(
                "type", "GROUP_MEMBER_REMOVED",
                "groupId", groupId,
                "message", "您已被移出群组"
            );
            messagingTemplate.convertAndSendToUser(
                String.valueOf(memberId), 
                "/queue/notifications", 
                notification
            );
            
            // 通知群组内其他成员刷新群组信息
            messagingTemplate.convertAndSend(
                "/topic/group/" + groupId + "/events",
                Map.of(
                    "type", "MEMBER_REMOVED",
                    "groupId", groupId,
                    "removedMemberId", memberId
                )
            );
            
            logger.info("发送成员移除通知: groupId={}, memberId={}", groupId, memberId);
        }
    }
    
    /**
     * 发送群名变更通知
     */
    private void sendGroupNameChangedNotification(JSONObject data) {
        String groupId = data.getString("groupId");
        String oldName = data.getString("oldName");
        String newName = data.getString("newName");
        
        if (groupId != null && newName != null) {
            // 通知群组内所有成员
            messagingTemplate.convertAndSend(
                "/topic/group/" + groupId + "/events",
                Map.of(
                    "type", "GROUP_NAME_CHANGED",
                    "groupId", groupId,
                    "oldName", oldName != null ? oldName : "",
                    "newName", newName
                )
            );
            
            logger.info("发送群名变更通知: groupId={}, oldName={}, newName={}", groupId, oldName, newName);
        }
    }
    
    /**
     * 发送群组解散通知
     */
    @SuppressWarnings("unchecked")
    private void sendGroupDissolvedNotification(JSONObject data) {
        String groupId = data.getString("groupId");
        String groupName = data.getString("groupName");
        Object allMembersObj = data.get("allMembers");
        
        if (groupId != null && allMembersObj != null) {
            try {
                // 解析成员列表
                List<Map<String, Object>> allMembers = (List<Map<String, Object>>) allMembersObj;
                
                // 通知所有原群成员
                for (Map<String, Object> member : allMembers) {
                    Object userIdObj = member.get("userId");
                    if (userIdObj != null) {
                        Integer userId = (Integer) userIdObj;
                        Map<String, Object> notification = Map.of(
                            "type", "GROUP_DISSOLVED",
                            "groupId", groupId,
                            "groupName", groupName != null ? groupName : "未知群组",
                            "message", "群组已被解散"
                        );
                        messagingTemplate.convertAndSendToUser(
                            String.valueOf(userId),
                            "/queue/notifications",
                            notification
                        );
                    }
                }
                
                logger.info("发送群组解散通知: groupId={}, groupName={}, memberCount={}", 
                           groupId, groupName, allMembers.size());
            } catch (Exception e) {
                logger.error("解析群组成员列表失败: groupId={}, allMembersObj={}", groupId, allMembersObj, e);
            }
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
    
    /**
     * 定时处理待处理的事件
     * 每5秒执行一次
     */
    @Scheduled(fixedDelay = 5000)
    public void processOutboxEventsScheduled() {
        try {
            int processed = processPendingEvents(50);
            if (processed > 0) {
                logger.debug("定时处理事件完成: 处理数量={}", processed);
            }
        } catch (Exception e) {
            logger.error("定时处理事件失败", e);
        }
    }
    
    /**
     * 定时重试失败的事件
     * 每30秒执行一次
     */
    @Scheduled(fixedDelay = 30000)
    public void retryFailedEventsScheduled() {
        try {
            int retried = retryFailedEvents(3, 20);
            if (retried > 0) {
                logger.info("定时重试失败事件完成: 重试数量={}", retried);
            }
        } catch (Exception e) {
            logger.error("定时重试失败事件失败", e);
        }
    }
} 