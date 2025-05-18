package com.example.service.impl;

import com.example.entity.vo.response.ChatMessage;
import com.example.listener.FriendRequestListener;
import com.example.service.ChatService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import java.util.concurrent.TimeUnit;
import java.util.List;
import com.example.service.AccountService;
import com.example.utils.Const;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.entity.dto.Friends;
import com.example.entity.dto.Group_member;
import com.example.mapper.FriendsMapper;
import com.example.mapper.Group_memberMapper;
import java.util.stream.Collectors;     
import com.example.entity.dto.Group_message;
import com.example.entity.dto.PrivateChatMessage;
import com.example.mapper.Group_messageMapper;
import com.example.utils.ConvertUtils;
import java.util.ArrayList;
import com.alibaba.fastjson2.*;
import com.example.entity.vo.response.FriendsResponse;
import com.example.entity.dto.Account;
import java.util.Map;
import java.util.HashMap;
import com.example.mapper.Private_messagesMapper;
import com.example.entity.vo.response.ChatMessage;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.service.ChatCacheService;
import com.example.service.OnlineStatusService;
/**
 * 聊天服务实现类，使用Redis存储最近消息，可以根据需要扩展为数据库存储
 */
@Service
public class ChatServiceImpl implements ChatService {

    private final FriendRequestListener friendRequestListener;

    @Resource
    AccountService accountService;

    @Resource
    FriendsMapper friendsMapper;

    @Resource
    Private_messagesMapper privateMessageMapper;

    @Resource
    Group_memberMapper groupMemberMapper;

    @Resource
    Group_messageMapper group_messageMapper;

    @Autowired
    private ChatCacheService chatCacheService;
    @Autowired
    private OnlineStatusService onlineStatusService;

    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);

    ChatServiceImpl(FriendRequestListener friendRequestListener) {
        this.friendRequestListener = friendRequestListener;
    }

    @Override
    public boolean savePublicMessage(ChatMessage message) {
        try {
            Group_message group_message = ConvertUtils.convertToGroupMessage(message);
            group_messageMapper.insert(group_message);
            chatCacheService.cacheGroupMessage(group_message.getGroupId(), JSON.toJSONString(group_message));
            logger.info("保存群组消息: {}, 发送者: {}", message.getContent(), message.getSender());
            return true;
        } catch (Exception e) {
            logger.error("保存群组消息失败", e);
            return false;
        }
    }

    @Override
    public boolean savePrivateMessage(ChatMessage message) {
        try {
            // 持久化到数据库
            PrivateChatMessage privateChatMessage = ConvertUtils.convertToPrivateChatMessage(message);
            privateMessageMapper.insert(privateChatMessage);
            // 缓存私聊消息及相关数据
            chatCacheService.cachePrivateMessage(message);
            logger.info("保存私人消息 (DB+Redis): {}, 从 {} 到 {}", privateChatMessage.getContent(), message.getSenderId(), message.getReceiverId());
            return true;
        } catch (Exception e) {
            logger.error("保存私人消息 (DB/Redis) 失败", e);
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

    @Override
    public List<FriendsResponse> getFriends(String userId) {
        List<FriendsResponse> friendsResponseList = new ArrayList<>();    
        List<Friends> friendsList = getFriendsByUserId(userId);
        System.out.println("用户" + userId + "的好友列表：" + friendsList);
        if (friendsList != null && !friendsList.isEmpty()) {
                friendsResponseList = friendsList.stream()
                    .map(friend -> {
                        FriendsResponse response = ConvertUtils.convertToFriendsResponse(friend, accountService);
                        
                        // 获取好友ID
                        String friendId;
                        if (userId.equals(response.getFirstUserId())) {
                            friendId = response.getSecondUserId();
                        } else {
                            friendId = response.getFirstUserId();
                        }
                        
                        // 查询在线状态
                        response.setOnline(onlineStatusService.isOnline(friendId));
                        
                        return response;
                    })
                    .collect(Collectors.toList());  
        }
        return friendsResponseList != null ? friendsResponseList : new ArrayList<>(); // 保证始终返回 List
    }

    @Override
    public List<FriendsResponse> getFriendRequests(String userId) {
        List<FriendsResponse> friendsResponseList = new ArrayList<>();
        List<Friends> friendsList = getFriendRequestsByUserId(userId);
        if (friendsList != null && !friendsList.isEmpty()) {
            friendsResponseList = friendsList.stream()
                .map(friend -> ConvertUtils.convertToFriendsResponse(friend, accountService))
                .collect(Collectors.toList());  
        }
        return friendsResponseList != null ? friendsResponseList : new ArrayList<>(); // 保证始终返回 List
    }

    @Override
    public List<Group_member> getGroups(String userId) {
        List<Group_member> groupsList = null;
        groupsList = getGroupsByUserId(userId);
        return groupsList != null ? groupsList : new ArrayList<>(); // 保证始终返回 List
    }

    public List<Friends> getFriendsByUserId(String userId){
      
        int id = Integer.parseInt(userId);
        List<Friends> friends = friendsMapper.selectList(
            Wrappers.<Friends>query().eq("the_second_user_id", id)
            .eq("status", Friends.Status.accepted)
            .or().eq("the_first_user_id", id)
            .eq("status", Friends.Status.accepted)
            .orderByDesc("created_at"));
        return friends;
    }

    public List<Friends> getFriendRequestsByUserId(String userId){
        int id = Integer.parseInt(userId);
        List<Friends> friends = friendsMapper.selectList(
            Wrappers.<Friends>query()
                .in("status", Friends.Status.requested, Friends.Status.accepted, Friends.Status.rejected)
                .and(q -> q.eq("the_second_user_id", id)
                           .or().eq("the_first_user_id", id))
                .orderByDesc("created_at")
        );
        //返回所有曾经的请求
        return friends;
    }
    
    public List<Group_member> getGroupsByUserId(String userId){
        try {
            int id = Integer.parseInt(userId);
            System.out.println("从数据库获取的群组列表: " + id);
            return groupMemberMapper.selectList(Wrappers.<Group_member>query().eq("user_id", id));
        } catch (NumberFormatException e) {
            logger.error("Invalid userId format: {}", userId, e);
            // 或者抛出自定义异常，或者返回空列表，根据你的错误处理策略决定
            return new ArrayList<>();
        }
    }

    @Override
    public boolean isFriend(int userId1, int userId2) {
        // 检查两个用户是否已经是好友关系
        List<Friends> friends = friendsMapper.selectList(Wrappers.<Friends>query()
            .eq("the_first_user_id", userId1)
            .eq("the_second_user_id", userId2)
            .eq("status", Friends.Status.accepted));
        
        if (!friends.isEmpty()) {
            return true;
        }

        // 检查反向关系
        friends = friendsMapper.selectList(Wrappers.<Friends>query()
            .eq("the_first_user_id", userId2)
            .eq("the_second_user_id", userId1)
            .eq("status", Friends.Status.accepted));

        return !friends.isEmpty();
    }

    @Override
    public boolean hasFriendRequest(int senderId, int receiverId) {
        // 检查是否已经存在好友请求
        List<Friends> requests = friendsMapper.selectList(Wrappers.<Friends>query()
            .eq("the_first_user_id", senderId)
            .eq("the_second_user_id", receiverId)
            .eq("status", Friends.Status.requested));
        
        if (!requests.isEmpty()) {
            return true;
        }
        return false;
    }

    @Override
    public boolean saveFriendRequest(Friends friendRequest) {
        try {
            if (friendRequest.getTheFirstUserId() == friendRequest.getTheSecondUserId()) {
                logger.error("不能添加自己为好友");
                return false;
            }
            
            friendsMapper.insert(friendRequest);
            // 通知被请求方有新的好友请求
            // 这里可以使用WebSocket通知前端
            return true;
        } catch (Exception e) {
            logger.error("保存好友请求失败", e);
            return false;
        }
    }

    @Override
    public boolean ReceivedFriendRequests(int senderId, int receiverId) {
        try {
            //能到这个方法，说明senderId向receiverId发送了好友请求
            //所以直接将好友状态设置为accepted
            friendsMapper.update(new LambdaUpdateWrapper<Friends>()
                .eq(Friends::getTheFirstUserId, senderId)
                .eq(Friends::getTheSecondUserId, receiverId)
                .set(Friends::getStatus, Friends.Status.accepted));
            return true;
        } catch (Exception e) {
            logger.error("接受好友请求失败", e);
            return false;
        }
    }

    @Override
    public boolean acceptFriendRequest(int requestId, int userId) {
        try {
            // 获取请求
            Friends request = friendsMapper.selectById(requestId);
            if (request == null || request.getTheSecondUserId() != userId || 
                request.getStatus() != Friends.Status.requested) {
                logger.error("好友请求不存在或无权操作");
                return false;
            }

            // 更新状态为已接受
            request.setStatus(Friends.Status.accepted);

            return friendsMapper.updateById(request) > 0;
        } catch (Exception e) {
            logger.error("接受好友请求失败", e);
            return false;
        }
    }

    @Override
    public boolean rejectFriendRequest(int requestId, int userId) {
        try {
            // 获取请求
            Friends request = friendsMapper.selectById(requestId);
            if (request == null || request.getTheSecondUserId() != userId ||
                request.getStatus() != Friends.Status.requested) {
                logger.error("好友请求不存在或无权操作");
                return false;
            }
            // 更新状态为已拒绝
            request.setStatus(Friends.Status.rejected);
            return friendsMapper.updateById(request) > 0;
        } catch (Exception e) {
            logger.error("拒绝好友请求失败", e);
            return false;
        }
    }

    @Override
    public boolean cancelFriendRequest(int currentUserId, int targetUserId) {
        try {
            LambdaUpdateWrapper<Friends> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Friends::getTheFirstUserId, currentUserId)
                         .eq(Friends::getTheSecondUserId, targetUserId)
                         .eq(Friends::getStatus, Friends.Status.requested)
                         .set(Friends::getStatus, Friends.Status.rejected);
            
            int updatedRows = friendsMapper.update(null, updateWrapper);
            if (updatedRows > 0) {
                logger.info("用户 {} 取消了发往用户 {} 的好友请求", currentUserId, targetUserId);
                return true;
            } else {
                logger.warn("取消好友请求未执行或未找到匹配的请求: currentUserId={}, targetUserId={}", currentUserId, targetUserId);
                return false; // 或根据业务返回特定状态
            }
        } catch (Exception e) {
            logger.error("取消好友请求失败: currentUserId={}, targetUserId={}", currentUserId, targetUserId, e);
            return false;
        }
    }

    @Override
    public boolean rejectFriendRequestByUsers(int currentUserId, int senderIdOfRequest) {
        try {
            LambdaUpdateWrapper<Friends> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Friends::getTheFirstUserId, senderIdOfRequest) // 请求的发送方
                         .eq(Friends::getTheSecondUserId, currentUserId)    // 请求的接收方 (当前操作用户)
                         .eq(Friends::getStatus, Friends.Status.requested)
                         .set(Friends::getStatus, Friends.Status.rejected);
            int updatedRows = friendsMapper.update(null, updateWrapper);
            if (updatedRows > 0) {
                logger.info("用户 {} 拒绝了来自用户 {} 的好友请求", currentUserId, senderIdOfRequest);
                return true;
            } else {
                logger.warn("拒绝好友请求未执行或未找到匹配的请求: receiverId={}, senderId={}", currentUserId, senderIdOfRequest);
                return false;
            }
        } catch (Exception e) {
            logger.error("拒绝好友请求失败: receiverId={}, senderId={}", currentUserId, senderIdOfRequest, e);
            return false;
        }
    }

    @Override
    public List<PrivateChatMessage> getPrivateChatHistory(int userId,int limit) {
        // 直接使用缓存服务获取列表
        return chatCacheService.getPrivateChatHistory(userId, limit);
    }

    @Override
    public List<PrivateChatMessage> getPrivateChatHistoryByUserIdAndFriendId(int userId, int friendId, int limit, String oldestMessageId) {
        try {
            //直接从数据库中获取私聊消息
            int id = Integer.parseInt(oldestMessageId);
            List<PrivateChatMessage> privateChatMessages = privateMessageMapper.selectList(
                Wrappers.<PrivateChatMessage>lambdaQuery()
                    // 条件1：A发给B的消息
                    .and(wq -> wq
                        .eq(PrivateChatMessage::getSenderId, userId)
                        .eq(PrivateChatMessage::getReceiverId, friendId)
                    )
                    // 条件2：或B发给A的消息
                    .or(wq -> wq
                        .eq(PrivateChatMessage::getSenderId, friendId)
                        .eq(PrivateChatMessage::getReceiverId, userId)
                    )
                    // 只查询比oldestMessageId更早的消息
                    .lt(PrivateChatMessage::getId, id)
                    // 按创建时间降序
                    .orderByDesc(PrivateChatMessage::getCreatedAt)
                    // 限制条数
                    .last("LIMIT " + limit)
            );
            if(privateChatMessages.size() < limit){
                return privateChatMessages;
            }
            privateChatMessages = privateChatMessages.subList(0, limit);
            return privateChatMessages;
        } catch (Exception e) {
            logger.error("获取私聊消息失败", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Group_message> getGroupChatHistoryByGroupId(String groupId, int limit) {
        // 委托缓存服务实现
        return chatCacheService.getGroupChatHistory(groupId, limit);
    }

    @Override
    public boolean removeFriend(int userId1, int userId2) {
        try {
            // 删除双向好友关系，更新状态为 deleted
            LambdaUpdateWrapper<Friends> wrapper1 = new LambdaUpdateWrapper<>();
            wrapper1.eq(Friends::getTheFirstUserId, userId1)
                    .eq(Friends::getTheSecondUserId, userId2)
                    .eq(Friends::getStatus, Friends.Status.accepted)
                    .set(Friends::getStatus, Friends.Status.deleted);

            LambdaUpdateWrapper<Friends> wrapper2 = new LambdaUpdateWrapper<>();
            wrapper2.eq(Friends::getTheFirstUserId, userId2)
                    .eq(Friends::getTheSecondUserId, userId1)
                    .eq(Friends::getStatus, Friends.Status.accepted)
                    .set(Friends::getStatus, Friends.Status.deleted);

            int updated = friendsMapper.update(null, wrapper1) + friendsMapper.update(null, wrapper2);
            if (updated > 0) {
                logger.info("用户 {} 与 {} 的好友关系已删除", userId1, userId2);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("删除好友失败: {} 和 {}", userId1, userId2, e);
            return false;
        }
    }
} 