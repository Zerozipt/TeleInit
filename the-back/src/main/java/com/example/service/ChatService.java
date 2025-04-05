package com.example.service;

import com.example.entity.vo.response.ChatMessage;
import com.example.entity.vo.response.PrivateChatMessage;
import java.util.List;
import com.example.entity.dto.Friends;
import com.example.entity.dto.Group_member;
import com.example.entity.vo.response.FriendsResponse;
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

    /**
     * 获取用户的好友列表
     * @param userId 用户ID
     * @return 好友列表 (FriendsResponse 包含好友基本信息)
     */
    List<FriendsResponse> getFriends(String userId);

    // 获取用户所在的群组信息
    List<Group_member> getGroups(String userId);
} 