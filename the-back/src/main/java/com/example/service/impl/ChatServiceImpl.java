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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        // P0优化：精确异常处理，分离数据库和缓存操作
        Group_message dbMessage = null;
        try {
            // 步骤1：先保存到数据库（关键路径）
            dbMessage = ConvertUtils.convertToGroupMessage(message);
            group_messageMapper.insert(dbMessage);
            logger.info("群组消息已保存到数据库: messageId={}, groupId={}, sender={}", 
                       dbMessage.getId(), message.getGroupId(), message.getSender());
            
            // 步骤2：异步更新缓存（非关键路径）
            asyncUpdateGroupMessageCache(dbMessage);
            return true;
            
        } catch (org.springframework.dao.DataAccessException e) {
            logger.error("群组消息数据库保存失败: groupId={}, sender={}", 
                        message.getGroupId(), message.getSender(), e);
            return false;
        } catch (Exception e) {
            logger.error("群组消息保存发生未知错误", e);
            // 如果数据库已保存，至少返回成功
            return dbMessage != null && dbMessage.getId() != null;
        }
    }

    @Override
    public String savePublicMessageWithId(ChatMessage message) {
        // P0优化：精确异常处理，确保返回准确的消息ID
        Group_message dbMessage = null;
        try {
            // 步骤1：先保存到数据库
            dbMessage = ConvertUtils.convertToGroupMessage(message);
            group_messageMapper.insert(dbMessage);
            logger.info("群组消息已保存到数据库: messageId={}, groupId={}, sender={}", 
                       dbMessage.getId(), message.getGroupId(), message.getSender());
            
            // 步骤2：异步更新缓存
            asyncUpdateGroupMessageCache(dbMessage);
            return String.valueOf(dbMessage.getId());
            
        } catch (org.springframework.dao.DataAccessException e) {
            logger.error("群组消息数据库保存失败: groupId={}, sender={}", 
                        message.getGroupId(), message.getSender(), e);
            return null;
        } catch (Exception e) {
            logger.error("群组消息保存发生未知错误", e);
            // 如果数据库已保存，返回消息ID
            if (dbMessage != null && dbMessage.getId() != null) {
                return String.valueOf(dbMessage.getId());
            }
            return null;
        }
    }

    @Override
    public boolean savePrivateMessage(ChatMessage message) {
        // P0优化：精确异常处理，分离数据库和缓存操作
        PrivateChatMessage dbMessage = null;
        try {
            // 步骤1：先保存到数据库（关键路径）
            dbMessage = ConvertUtils.convertToPrivateChatMessage(message);
            privateMessageMapper.insert(dbMessage);
            logger.info("私聊消息已保存到数据库: messageId={}, from={}, to={}", 
                       dbMessage.getId(), message.getSenderId(), message.getReceiverId());
            
            // 步骤2：异步更新缓存（非关键路径）
            asyncUpdatePrivateMessageCache(message, dbMessage.getId());
            return true;
            
        } catch (org.springframework.dao.DataAccessException e) {
            logger.error("私聊消息数据库保存失败: from={}, to={}", 
                        message.getSenderId(), message.getReceiverId(), e);
            return false;
        } catch (Exception e) {
            logger.error("私聊消息保存发生未知错误", e);
            // 如果数据库已保存，至少返回成功
            return dbMessage != null && dbMessage.getId() != null;
        }
    }
    
    @Override
    public String savePrivateMessageWithId(ChatMessage message) {
        // P0优化：精确异常处理，确保返回准确的消息ID
        PrivateChatMessage dbMessage = null;
        try {
            // 步骤1：先保存到数据库
            dbMessage = ConvertUtils.convertToPrivateChatMessage(message);
            privateMessageMapper.insert(dbMessage);
            logger.info("私聊消息已保存到数据库: messageId={}, from={}, to={}", 
                       dbMessage.getId(), message.getSenderId(), message.getReceiverId());
            
            // 步骤2：异步更新缓存
            asyncUpdatePrivateMessageCache(message, dbMessage.getId());
            return String.valueOf(dbMessage.getId());
            
        } catch (org.springframework.dao.DataAccessException e) {
            logger.error("私聊消息数据库保存失败: from={}, to={}", 
                        message.getSenderId(), message.getReceiverId(), e);
            return null;
        } catch (Exception e) {
            logger.error("私聊消息保存发生未知错误", e);
            // 如果数据库已保存，返回消息ID
            if (dbMessage != null && dbMessage.getId() != null) {
                return String.valueOf(dbMessage.getId());
            }
            return null;
        }
    }
    
    /**
     * 异步更新群组消息缓存
     */
    private void asyncUpdateGroupMessageCache(Group_message dbMessage) {
        try {
            String messageJson = JSON.toJSONString(dbMessage);
            chatCacheService.cacheGroupMessage(dbMessage.getGroupId(), messageJson);
            logger.debug("群组消息缓存更新成功: messageId={}", dbMessage.getId());
        } catch (Exception e) {
            logger.warn("群组消息缓存更新失败，但消息已保存: messageId={}", dbMessage.getId(), e);
            // 可以考虑加入重试队列或者触发缓存重建任务
        }
    }
    
    /**
     * 异步更新私聊消息缓存
     */
    private void asyncUpdatePrivateMessageCache(ChatMessage message, Long messageId) {
        try {
            // 设置实际的消息ID
            message.setId(String.valueOf(messageId));
            chatCacheService.cachePrivateMessage(message);
            logger.debug("私聊消息缓存更新成功: messageId={}", messageId);
        } catch (Exception e) {
            logger.warn("私聊消息缓存更新失败，但消息已保存: messageId={}", messageId, e);
            // 可以考虑加入重试队列或者触发缓存重建任务
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
        
        // 修改查询逻辑：按用户对查找最新的好友记录，只返回状态为accepted的最新记录
        List<Friends> allFriendRecords = friendsMapper.selectList(
            Wrappers.<Friends>query()
                .and(q -> q.eq("the_second_user_id", id).or().eq("the_first_user_id", id))
                .orderByDesc("created_at")
        );
        
        // 按用户对分组，只保留每对用户之间最新的accepted记录
        Map<String, Friends> latestFriendships = new HashMap<>();
        
        for (Friends friend : allFriendRecords) {
            // 生成用户对的唯一键（确保A-B和B-A是同一个键）
            String userPairKey = generateUserPairKey(friend.getTheFirstUserId(), friend.getTheSecondUserId());
            
            // 只有状态为accepted且还没有该用户对的记录时才保留
            if (friend.getStatus() == Friends.Status.accepted && !latestFriendships.containsKey(userPairKey)) {
                latestFriendships.put(userPairKey, friend);
            }
        }
        
        return new ArrayList<>(latestFriendships.values());
    }

    public List<Friends> getFriendRequestsByUserId(String userId){
        int id = Integer.parseInt(userId);
        
        // 返回所有历史请求记录（用于前端展示历史）
        List<Friends> friends = friendsMapper.selectList(
            Wrappers.<Friends>query()
                .in("status", Friends.Status.requested, Friends.Status.accepted, Friends.Status.rejected)
                .and(q -> q.eq("the_second_user_id", id)
                           .or().eq("the_first_user_id", id))
                .orderByDesc("created_at")
        );
        
        return friends;
    }
    
    // 新增辅助方法：生成用户对的唯一键
    private String generateUserPairKey(int userId1, int userId2) {
        return userId1 < userId2 ? userId1 + "_" + userId2 : userId2 + "_" + userId1;
    }
    
    // 新增方法：获取两个用户之间的最新请求记录
    private Friends getLatestFriendRequest(int senderId, int receiverId) {
        List<Friends> requests = friendsMapper.selectList(
            Wrappers.<Friends>query()
                .eq("the_first_user_id", senderId)
                .eq("the_second_user_id", receiverId)
                .orderByDesc("created_at")
                .last("LIMIT 1")
        );
        
        return requests.isEmpty() ? null : requests.get(0);
    }

    @Override
    public boolean isFriend(int userId1, int userId2) {
        // 修改逻辑：检查两个用户之间最新的记录是否为accepted状态
        List<Friends> allRecords = friendsMapper.selectList(
            Wrappers.<Friends>query()
                .and(q -> q
                    .and(subQ -> subQ.eq("the_first_user_id", userId1).eq("the_second_user_id", userId2))
                    .or(subQ -> subQ.eq("the_first_user_id", userId2).eq("the_second_user_id", userId1))
                )
                .orderByDesc("created_at")
                .last("LIMIT 1")
        );
        
        return !allRecords.isEmpty() && allRecords.get(0).getStatus() == Friends.Status.accepted;
    }

    @Override
    public boolean hasFriendRequest(int senderId, int receiverId) {
        // 修改逻辑：检查是否已经存在未处理的好友请求（最新记录为requested状态）
        Friends latestRequest = getLatestFriendRequest(senderId, receiverId);
        return latestRequest != null && latestRequest.getStatus() == Friends.Status.requested;
    }

    @Override
    public boolean ReceivedFriendRequests(int senderId, int receiverId) {
        // P0优化：使用乐观锁防止并发更新冲突
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // 获取最新的请求记录
                Friends latestRequest = getLatestFriendRequest(senderId, receiverId);
                
                if (latestRequest == null || latestRequest.getStatus() != Friends.Status.requested) {
                    logger.warn("没有找到有效的好友请求: senderId={}, receiverId={}", senderId, receiverId);
                    return false;
                }
                
                // 使用乐观锁更新状态
                latestRequest.setStatus(Friends.Status.accepted);
                int updatedRows = friendsMapper.updateById(latestRequest);
                
                if (updatedRows > 0) {
                    logger.info("用户 {} 接受了来自用户 {} 的好友请求，记录ID: {}, 尝试次数: {}", 
                               receiverId, senderId, latestRequest.getId(), attempt);
                    return true;
                } else {
                    logger.warn("好友请求状态更新失败，可能存在并发冲突: senderId={}, receiverId={}, 尝试次数: {}", 
                               senderId, receiverId, attempt);
                    if (attempt < maxRetries) {
                        // 短暂等待后重试
                        Thread.sleep(50 * attempt);
                        continue;
                    }
                }
            } catch (Exception e) {
                logger.error("接受好友请求失败，尝试次数: {}", attempt, e);
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(100 * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    throw new RuntimeException("接受好友请求失败，已重试" + maxRetries + "次", e);
                }
            }
        }
        return false;
    }

    @Override
    public boolean rejectFriendRequestByUsers(int currentUserId, int senderIdOfRequest) {
        // P0优化：使用乐观锁防止并发更新冲突
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Friends latestRequest = getLatestFriendRequest(senderIdOfRequest, currentUserId);
                
                if (latestRequest == null || latestRequest.getStatus() != Friends.Status.requested) {
                    logger.warn("没有找到有效的好友请求: senderId={}, receiverId={}", senderIdOfRequest, currentUserId);
                    return false;
                }
                
                // 使用乐观锁更新状态
                latestRequest.setStatus(Friends.Status.rejected);
                int updatedRows = friendsMapper.updateById(latestRequest);
                
                if (updatedRows > 0) {
                    logger.info("用户 {} 拒绝了来自用户 {} 的好友请求，记录ID: {}, 尝试次数: {}", 
                               currentUserId, senderIdOfRequest, latestRequest.getId(), attempt);
                    return true;
                } else {
                    logger.warn("好友请求状态更新失败，可能存在并发冲突: senderId={}, receiverId={}, 尝试次数: {}", 
                               senderIdOfRequest, currentUserId, attempt);
                    if (attempt < maxRetries) {
                        Thread.sleep(50 * attempt);
                        continue;
                    }
                }
            } catch (Exception e) {
                logger.error("拒绝好友请求失败，尝试次数: {}", attempt, e);
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(100 * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    throw new RuntimeException("拒绝好友请求失败，已重试" + maxRetries + "次", e);
                }
            }
        }
        return false;
    }

    @Override
    public boolean cancelFriendRequest(int currentUserId, int targetUserId) {
        // P0优化：使用乐观锁防止并发更新冲突
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Friends latestRequest = getLatestFriendRequest(currentUserId, targetUserId);
                
                if (latestRequest == null || latestRequest.getStatus() != Friends.Status.requested) {
                    logger.warn("没有找到有效的好友请求可以取消: senderId={}, receiverId={}", currentUserId, targetUserId);
                    return false;
                }
                
                // 使用乐观锁更新状态为rejected（取消）
                latestRequest.setStatus(Friends.Status.rejected);
                int updatedRows = friendsMapper.updateById(latestRequest);
                
                if (updatedRows > 0) {
                    logger.info("用户 {} 取消了发往用户 {} 的好友请求，记录ID: {}, 尝试次数: {}", 
                               currentUserId, targetUserId, latestRequest.getId(), attempt);
                    return true;
                } else {
                    logger.warn("好友请求取消失败，可能存在并发冲突: senderId={}, receiverId={}, 尝试次数: {}", 
                               currentUserId, targetUserId, attempt);
                    if (attempt < maxRetries) {
                        Thread.sleep(50 * attempt);
                        continue;
                    }
                }
            } catch (Exception e) {
                logger.error("取消好友请求失败，尝试次数: {}", attempt, e);
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(100 * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    throw new RuntimeException("取消好友请求失败，已重试" + maxRetries + "次", e);
                }
            }
        }
        return false;
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
} 