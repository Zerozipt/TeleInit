package com.example.service.impl;

import com.example.entity.vo.response.ChatMessage;
import com.example.entity.vo.response.PrivateChatMessage;
import com.example.service.ChatService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import java.util.concurrent.TimeUnit;

/**
 * 聊天服务实现类，使用Redis存储最近消息，可以根据需要扩展为数据库存储
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);
    private static final String PUBLIC_CHAT_KEY = "chat:public:";
    private static final String PRIVATE_CHAT_KEY = "chat:private:";
    private static final int MESSAGE_EXPIRE_DAYS = 7; // 消息保存7天

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean savePublicMessage(ChatMessage message) {
        try {
            String messageId = message.getSender() + ":" + message.getTimestamp().getTime();
            String key = PUBLIC_CHAT_KEY + (message.getRoomId() != null ? message.getRoomId() : "default");
            
            // 将消息转为JSON保存
            stringRedisTemplate.opsForList().rightPush(key, JSON.toJSONString(message));
            // 设置过期时间
            stringRedisTemplate.expire(key, MESSAGE_EXPIRE_DAYS, TimeUnit.DAYS);
            
            logger.info("保存公共消息: {}, 发送者: {}", message.getContent(), message.getSender());
            return true;
        } catch (Exception e) {
            logger.error("保存公共消息失败", e);
            return false;
        }
    }

    @Override
    public boolean savePrivateMessage(PrivateChatMessage message) {
        try {
            // 构建发送者与接收者之间的对话键
            String conversationKey = PRIVATE_CHAT_KEY + getConversationId(message.getFromUser(), message.getToUser());
            
            // 将消息转为JSON保存
            stringRedisTemplate.opsForList().rightPush(conversationKey, JSON.toJSONString(message));
            // 设置过期时间
            stringRedisTemplate.expire(conversationKey, MESSAGE_EXPIRE_DAYS, TimeUnit.DAYS);
            
            logger.info("保存私人消息: {}, 从 {} 到 {}", message.getContent(), message.getFromUser(), message.getToUser());
            return true;
        } catch (Exception e) {
            logger.error("保存私人消息失败", e);
            return false;
        }
    }
    
    /**
     * 获取会话ID，确保相同的两个用户会得到相同的会话ID
     */
    private String getConversationId(String user1, String user2) {
        // 确保会话ID的一致性，无论是用户A发给用户B，还是用户B发给用户A
        return user1.compareTo(user2) < 0 ? user1 + ":" + user2 : user2 + ":" + user1;
    }
} 