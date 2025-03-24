package com.example.service;

import com.alibaba.fastjson2.JSON;
import com.example.entity.dto.Account;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket聊天服务
 */
@Slf4j
@Component
@ServerEndpoint("/chat/{userId}")
public class WebSocketChatService {
    
    /**
     * 用于存储所有在线用户的WebSocket会话
     * key: userId, value: WebSocket会话
     */
    private static final Map<String, Session> ONLINE_USERS = new ConcurrentHashMap<>();
    
    /**
     * 当WebSocket连接建立时调用
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) {
        ONLINE_USERS.put(userId, session);
        log.info("用户连接成功: {}, 当前在线人数: {}", userId, ONLINE_USERS.size());
        
        // 通知所有人有新用户上线
        broadcastUserStatus(userId, "online");
    }
    
    /**
     * 当接收到WebSocket消息时调用
     */
    @OnMessage
    public void onMessage(String message, Session session, @PathParam("userId") String userId) {
        log.info("收到用户{}的消息: {}", userId, message);
        
        try {
            // 解析消息（这里假设消息是JSON格式，包含接收者ID和消息内容）
            Map<String, Object> messageMap = JSON.parseObject(message);
            String toUserId = (String) messageMap.get("to");
            String content = (String) messageMap.get("content");
            
            // 创建完整消息对象
            Map<String, Object> chatMessage = new ConcurrentHashMap<>();
            chatMessage.put("from", userId);
            chatMessage.put("to", toUserId);
            chatMessage.put("content", content);
            chatMessage.put("timestamp", System.currentTimeMillis());
            
            String jsonMessage = JSON.toJSONString(chatMessage);
            
            // 发送私信
            if (toUserId != null && !toUserId.isEmpty()) {
                sendMessageToUser(toUserId, jsonMessage);
            } else {
                // 如果没有指定接收者，则广播给所有人
                broadcastMessage(jsonMessage);
            }
        } catch (Exception e) {
            log.error("处理消息时出错", e);
        }
    }
    
    /**
     * 当WebSocket连接关闭时调用
     */
    @OnClose
    public void onClose(@PathParam("userId") String userId) {
        ONLINE_USERS.remove(userId);
        log.info("用户断开连接: {}, 当前在线人数: {}", userId, ONLINE_USERS.size());
        
        // 通知所有人有用户下线
        broadcastUserStatus(userId, "offline");
    }
    
    /**
     * 当WebSocket发生错误时调用
     */
    @OnError
    public void onError(Session session, Throwable error, @PathParam("userId") String userId) {
        log.error("用户{}的WebSocket连接发生错误: {}", userId, error.getMessage());
        error.printStackTrace();
    }
    
    /**
     * 向指定用户发送消息
     */
    public void sendMessageToUser(String userId, String message) {
        Session session = ONLINE_USERS.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                log.error("向用户{}发送消息失败", userId, e);
            }
        } else {
            log.warn("用户{}不在线，消息发送失败", userId);
            // 这里可以实现离线消息存储逻辑
        }
    }
    
    /**
     * 广播消息给所有在线用户
     */
    public void broadcastMessage(String message) {
        ONLINE_USERS.forEach((userId, session) -> {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    log.error("向用户{}广播消息失败", userId, e);
                }
            }
        });
    }
    
    /**
     * 广播用户状态变更消息
     */
    private void broadcastUserStatus(String userId, String status) {
        Map<String, Object> statusMessage = new ConcurrentHashMap<>();
        statusMessage.put("type", "status");
        statusMessage.put("userId", userId);
        statusMessage.put("status", status);
        statusMessage.put("timestamp", System.currentTimeMillis());
        
        broadcastMessage(JSON.toJSONString(statusMessage));
    }
    
    /**
     * 获取当前在线用户数量
     */
    public static int getOnlineUserCount() {
        return ONLINE_USERS.size();
    }
    
    /**
     * 获取所有在线用户的ID
     */
    public static String[] getOnlineUserIds() {
        return ONLINE_USERS.keySet().toArray(new String[0]);
    }
} 