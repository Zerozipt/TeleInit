package com.example.service.impl;

import com.example.entity.vo.response.ChatMessage;
import com.example.entity.vo.response.PrivateChatMessage;
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
import com.example.mapper.Group_messageMapper;
import com.example.utils.ConvertUtils;
import java.util.ArrayList;
import com.alibaba.fastjson2.*;
import com.example.entity.vo.response.FriendsResponse;
import com.example.entity.dto.Account;

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
    Group_memberMapper groupMemberMapper;

    @Resource
    Group_messageMapper group_messageMapper;

    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    ChatServiceImpl(FriendRequestListener friendRequestListener) {
        this.friendRequestListener = friendRequestListener;
    }

    @Override
    public boolean savePublicMessage(ChatMessage message) {
        try {
            Group_message group_message = ConvertUtils.convertToGroupMessage(message);
            group_messageMapper.insert(group_message);
            logger.info("保存群组消息: {}, 发送者: {}", message.getContent(), message.getSender());
            return true;
        } catch (Exception e) {
            logger.error("保存群组消息失败", e);
            return false;
        }
    }

    @Override
    public boolean savePrivateMessage(PrivateChatMessage message) {
        try {
            // 构建发送者与接收者之间的对话键
            String conversationKey = Const.PRIVATE_CHAT_KEY + getConversationId(message.getFromUser(), message.getToUser());    
            // 将消息转为JSON保存
            stringRedisTemplate.opsForList().rightPush(conversationKey, JSON.toJSONString(message));
            // 设置过期时间
            stringRedisTemplate.expire(conversationKey, Const.MESSAGE_EXPIRE_DAYS, TimeUnit.DAYS);
            
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

    @Override
    public List<FriendsResponse> getFriends(String userId) {
        String key = Const.FRIENDS_WITH_USER_ID + ":" + userId;
        List<FriendsResponse> friendsResponseList = null;

        try {
            // 1. 尝试从 Redis (String 类型) 获取缓存
            String cachedFriendsJson = stringRedisTemplate.opsForValue().get(key);

            if (cachedFriendsJson != null && !cachedFriendsJson.isEmpty()) {
                // 2. 缓存命中：反序列化 JSON 字符串为 List<FriendsResponse>
                try {
                    friendsResponseList = JSON.parseArray(cachedFriendsJson, FriendsResponse.class);
                    logger.debug("Cache hit for friends list: {}", key); // 记录缓存命中
                } catch (JSONException e) {
                    logger.error("Failed to parse cached friends JSON for key {}: {}", key, e.getMessage());
                    // 解析失败，当作缓存未命中处理，后续会从数据库加载
                    friendsResponseList = null;
                }
            }
        } catch (Exception e) {
            logger.error("Redis error when getting friends list for key {}: {}", key, e.getMessage(), e);
             // Redis异常，暂时当作缓存未命中，后续从数据库加载
             friendsResponseList = null;
        }
        // 3. 缓存未命中或解析失败：从数据库获取
        if (friendsResponseList == null) {
            logger.debug("Cache miss or parse error for friends list: {}", key); // 记录缓存未命中

            friendsResponseList = new ArrayList<>();
            int currentUserId = Integer.parseInt(userId);        
            List<Friends> friendsList = getFriendsByUserId(userId);
            System.out.println("用户" + userId + "的好友列表：" + friendsList);
            if (friendsList != null && !friendsList.isEmpty()) {
                 for (Friends friendRelation : friendsList) {
                    int friendId = friendRelation.getTheFirstUserId() == currentUserId
                                    ? friendRelation.getTheSecondUserId()
                                    : friendRelation.getTheFirstUserId();
                    System.out.println(friendId);
                    Account friendAccount = accountService.getAccountById(friendId);
                    if (friendAccount != null) {
                        friendsResponseList.add(ConvertUtils.convertToFriendsResponse(friendAccount));
                    } else {
                        logger.warn("Could not find account details for friend ID: {}", friendId);
                        // 可以选择跳过或添加一个占位符
                    }
                }
            }

            // 4. 将从数据库获取的数据存入 Redis 缓存 (序列化为 JSON 字符串)
            //    并设置一个过期时间 (例如：1 小时)
           System.out.println(friendRequestListener);
            if (friendsResponseList != null && !friendsResponseList.isEmpty()) { // 仅在列表非空时缓存
                 try {
                    String jsonToCache = JSON.toJSONString(friendsResponseList);
                    stringRedisTemplate.opsForValue().set(key, jsonToCache, 1, TimeUnit.HOURS); // 设置1小时过期
                    logger.debug("Cached friends list for key {}", key);
                 } catch (Exception e) {
                    logger.error("Redis error when setting friends list for key {}: {}", key, e.getMessage(), e);
                     // Redis set 失败，不影响返回结果，但需记录日志
                 }
            }
        }

        // 返回最终结果 (可能来自缓存或数据库)
        // 如果数据库也查不到，friendsResponseList 可能为 null 或 empty list
        return friendsResponseList != null ? friendsResponseList : new ArrayList<>(); // 保证始终返回 List
    }

    @Override
    public List<Group_member> getGroups(String userId) {
        String key = Const.GROUP_CHAT_KEY + ":" + userId;
        List<Group_member> groupsList = null;

        try {
            // 1. 尝试从 Redis (String 类型) 获取缓存
            String cachedGroupsJson = stringRedisTemplate.opsForValue().get(key);

            if (cachedGroupsJson != null && !cachedGroupsJson.isEmpty()) {
                // 2. 缓存命中：反序列化 JSON 字符串为 List<Group_member>
                 try {
                    groupsList = JSON.parseArray(cachedGroupsJson, Group_member.class);
                    logger.debug("Cache hit for group list: {}", key);
                 } catch (JSONException e) {
                    logger.error("Failed to parse cached group JSON for key {}: {}", key, e.getMessage());
                    groupsList = null; // 解析失败，当作缓存未命中
                 }
            }
        } catch (Exception e) {
             logger.error("Redis error when getting group list for key {}: {}", key, e.getMessage(), e);
             groupsList = null; // Redis 异常，当作缓存未命中
        }


        // 3. 缓存未命中或解析失败：从数据库获取
        if (groupsList == null) {
             logger.debug("Cache miss or parse error for group list: {}", key);
             groupsList = getGroupsByUserId(userId);
             // 4. 将从数据库获取的数据存入 Redis 缓存 (序列化为 JSON 字符串)
             //    并设置过期时间 (例如：1 小时)
             if (groupsList != null) {
                 try {
                    String jsonToCache = JSON.toJSONString(groupsList);
                    stringRedisTemplate.opsForValue().set(key, jsonToCache, 1, TimeUnit.HOURS); // 设置1小时过期
                    logger.debug("Cached group list for key {}", key);
                 } catch (Exception e) {
                    logger.error("Redis error when setting group list for key {}: {}", key, e.getMessage(), e);
                    // Redis set 失败，记录日志
                 }
             }
        }

        // 返回最终结果
        return groupsList != null ? groupsList : new ArrayList<>(); // 保证始终返回 List
    }

    public List<Friends> getFriendsByUserId(String userId){
        //从数据库中获取好友信息,返回好友的id列表
        //数据库的设计规则是，好友关系是双向的，即如果用户A是用户B的好友，那么用户B也是用户A的好友
        //所以需要从数据库中获取两次好友信息，然后合并
        int id = Integer.parseInt(userId);
        List<Friends> friends = friendsMapper.selectList(Wrappers.<Friends>query().eq("the_first_user_id", id));
        List<Friends> friends2 = friendsMapper.selectList(Wrappers.<Friends>query().eq("the_second_user_id", id));
        //合并两个列表
        List<Friends> friends3 = new ArrayList<>();
        friends3.addAll(friends);
        friends3.addAll(friends2);
        return friends3;
    }

    
    public List<Group_member> getGroupsByUserId(String userId){
        int id = Integer.parseInt(userId);
        return groupMemberMapper.selectList(Wrappers.<Group_member>query().eq("user_id", id));
    }
} 