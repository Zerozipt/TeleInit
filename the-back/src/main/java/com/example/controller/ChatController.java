package com.example.controller;

import com.example.entity.RestBean;
import com.example.service.WebSocketChatService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 聊天相关控制器
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    
    @Resource
    private WebSocketChatService webSocketChatService;
    
    /**
     * 获取在线用户信息
     */
    @GetMapping("/online")
    public RestBean<Map<String, Object>> getOnlineUsers() {
        Map<String, Object> data = new HashMap<>();
        data.put("count", WebSocketChatService.getOnlineUserCount());
        data.put("users", WebSocketChatService.getOnlineUserIds());
        return RestBean.success(data);
    }
} 