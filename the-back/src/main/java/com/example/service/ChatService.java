package com.example.service;

import com.example.entity.vo.response.ChatMessage;
import com.example.entity.vo.response.PrivateChatMessage;

/**
 * 聊天服务接口，处理消息的保存和检索
 */
public interface ChatService {
    
    /**
     * 保存公共聊天消息
     * @param message 聊天消息
     * @return 操作结果
     */
    boolean savePublicMessage(ChatMessage message);
    
    /**
     * 保存私人聊天消息
     * @param message 私人聊天消息
     * @return 操作结果
     */
    boolean savePrivateMessage(PrivateChatMessage message);
} 