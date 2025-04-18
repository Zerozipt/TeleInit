package com.example.service;

import com.example.entity.vo.response.ChatMessage;
import com.example.entity.vo.response.PrivateChatMessage;
import java.util.List;
import java.util.Map;
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

    /**
     * 检查两个用户是否已经是好友关系
     * @param userId1 第一个用户ID
     * @param userId2 第二个用户ID
     * @return 如果是好友返回true，否则返回false
     */
    boolean isFriend(int userId1, int userId2);

    /**
     * 检查是否已经存在好友请求
     * @param senderId 发送者ID
     * @param receiverId 接收者ID
     * @return 如果存在请求返回true，否则返回false
     */
    boolean hasFriendRequest(int senderId, int receiverId);

    /**
     * 保存好友请求
     * @param friendRequest 好友请求对象
     * @return 保存成功返回true，否则返回false
     */
    boolean saveFriendRequest(Friends friendRequest);

    /**
     * 获取用户收到的好友请求列表
     * @param userId 用户ID
     * @return 好友请求列表
     */
    List<Map<String, Object>> getReceivedFriendRequests(int userId);

    /**
     * 接受好友请求
     * @param requestId 好友请求ID
     * @param userId 当前用户ID
     * @return 操作成功返回true，否则返回false
     */
    boolean acceptFriendRequest(int requestId, int userId);

    /**
     * 拒绝好友请求
     * @param requestId 好友请求ID
     * @param userId 当前用户ID
     * @return 操作成功返回true，否则返回false
     */
    boolean rejectFriendRequest(int requestId, int userId);

    /**
     * 获取两个用户之间的私聊消息历史
     * @param user1 第一个用户名
     * @param user2 第二个用户名
     * @param limit 限制返回消息数量，如果为0则返回所有消息
     * @return 私聊消息列表
     */
    List<PrivateChatMessage> getPrivateChatHistory(String user1, String user2, int limit);
    
    /**
     * 获取群组的聊天消息历史
     * @param groupId 群组ID
     * @param limit 限制返回消息数量，如果为0则返回所有消息
     * @return 群组消息列表
     */
    List<ChatMessage> getGroupChatHistory(String groupId, int limit);
} 