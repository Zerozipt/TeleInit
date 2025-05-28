package com.example.service;

import com.example.entity.dto.PrivateChatMessage;
import com.example.entity.dto.Group_message;
import com.example.entity.vo.response.ChatMessage;
import java.util.List;

public interface ChatCacheService {
    /**
     * 缓存群聊消息
     * @param groupId 群ID
     * @param messageJson JSON字符串格式的消息
     */
    void cacheGroupMessage(String groupId, String messageJson);

    /**
     * 获取群聊历史（自动回落 DB 并缓存到 Redis）
     * @param groupId 群ID
     * @param limit 限制条数
     * @return 群聊消息列表
     */
    List<Group_message> getGroupChatHistory(String groupId, int limit);

    /**
     * 缓存私聊消息
     * @param message 聊天消息对象
     */
    void cachePrivateMessage(ChatMessage message);

    /**
     * 获取私聊历史（基于 Redis 缓存，自动回落 DB）
     * @param userId 用户ID
     * @param limit 限制条数
     * @return 私聊消息列表
     */
    List<PrivateChatMessage> getPrivateChatHistory(int userId, int limit);

    /**
     * 清除用户相关的聊天缓存
     * @param userId 用户ID
     */
    void clearUserChatCache(Integer userId);
} 