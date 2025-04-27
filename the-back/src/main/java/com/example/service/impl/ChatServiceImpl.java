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
    public boolean savePrivateMessage(ChatMessage message) {
        try {
            // 构建发送者与接收者之间的对话键
            PrivateChatMessage privateChatMessage = ConvertUtils.convertToPrivateChatMessage(message);
            privateMessageMapper.insert(privateChatMessage);
            logger.info("保存私人消息: {}, 从 {} 到 {}", privateChatMessage.getContent(), privateChatMessage.getSenderId(), privateChatMessage.getReceiverId());
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
            List<Friends> friendsList = getFriendsByUserId(userId);
            System.out.println("用户" + userId + "的好友列表：" + friendsList);
            if (friendsList != null && !friendsList.isEmpty()) {
                friendsResponseList = friendsList.stream()
                    .map(friend -> ConvertUtils.convertToFriendsResponse(friend, accountService))
                    .collect(Collectors.toList());
            }

            // 4. 将从数据库获取的数据存入 Redis 缓存 (序列化为 JSON 字符串)
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
    public List<FriendsResponse> getFriendRequests(String userId) {
        List<FriendsResponse> friendsResponseList = new ArrayList<>();        
        List<Friends> friendsList = getFriendRequestsByUserId(userId);
        if (friendsList != null && !friendsList.isEmpty()) {
            for (Friends friendRelation : friendsList) {
                friendsResponseList.add(ConvertUtils.convertToFriendsResponse(friendRelation, accountService));
           }
       }
        return friendsResponseList;
    }

    @Override
    public List<Group_member> getGroups(String userId) {
        String key = Const.GROUP_CHAT_MEMBER_KEY + ":" + userId;
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
             System.out.println("从数据库获取的群组列表: " + groupsList);
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
        //筛选的时候，筛选出status为accepted的好友
        int id = Integer.parseInt(userId);
        List<Friends> friends = friendsMapper.selectList(Wrappers.<Friends>query().eq("the_first_user_id", id).eq("status", Friends.Status.accepted));
        List<Friends> friends2 = friendsMapper.selectList(Wrappers.<Friends>query().eq("the_second_user_id", id).eq("status", Friends.Status.accepted));
        //合并两个列表
        List<Friends> friends3 = new ArrayList<>();
        friends3.addAll(friends);
        friends3.addAll(friends2);
        return friends3;
    }

    public List<Friends> getFriendRequestsByUserId(String userId){
        int id = Integer.parseInt(userId);
        //从数据库中获取好友请求信息,返回好友请求的id列表
        //数据库的设计是，好友请求类的话只会存储一条，意味用户1向用户2请求
        List<Friends> friends = friendsMapper.selectList(Wrappers.<Friends>query().eq("the_second_user_id", id));
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

        // 检查反向请求
        requests = friendsMapper.selectList(Wrappers.<Friends>query()
            .eq("the_first_user_id", receiverId)
            .eq("the_second_user_id", senderId)
            .eq("status", Friends.Status.requested));

        return !requests.isEmpty();
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
    public List<Map<String, Object>> getReceivedFriendRequests(int userId) {
        try {
            // 获取所有接收到的好友请求（作为第二个用户）
            List<Friends> requests = friendsMapper.selectList(Wrappers.<Friends>query()
                    .eq("the_second_user_id", userId)
                    .eq("status", Friends.Status.requested)
                    .orderByDesc("created_at"));

            // 转换为前端需要的格式，包括请求发送者的信息
            return requests.stream().map(request -> {
                Account sender = accountService.getAccountById(request.getTheFirstUserId());
                Map<String, Object> result = new HashMap<>();
                result.put("id", request.getId());
                result.put("fromUserId", sender.getId());
                result.put("fromUsername", sender.getUsername());
                result.put("createTime", request.getCreatedAt());
                return result;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("获取好友请求失败", e);
            return new ArrayList<>();
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
            request.setStatus(Friends.Status.deleted);
            return friendsMapper.updateById(request) > 0;
        } catch (Exception e) {
            logger.error("拒绝好友请求失败", e);
            return false;
        }
    }

    @Override
    public List<PrivateChatMessage> getPrivateChatHistory(int userId,int limit) {
        try {
            // 构建私聊消息键，将userId转换为字符串
            String privateKey = Const.PRIVATE_CHAT_KEY + String.valueOf(userId);
            // 获取Redis中存储的消息数量
            Long size = stringRedisTemplate.opsForList().size(privateKey);
            if (size == null || size == 0) { //如果私聊消息为空，则从数据库中获取
                logger.info("没有找到私聊消息: {}", privateKey);
                //从数据库中获取私聊消息,包括sender_id和receiver_id,只要sender_id或receiver_id中有一个是userId，就获取，并且以时间created_at排序
                List<PrivateChatMessage> privateChatMessages = privateMessageMapper.selectList(Wrappers.<PrivateChatMessage>query().eq("sender_id", userId).or().eq("receiver_id", userId).orderByDesc("created_at"));
                //将私聊消息保存到Redis中
                stringRedisTemplate.opsForList().rightPush(privateKey, JSON.toJSONString(privateChatMessages));
                //设置过期时间
                stringRedisTemplate.expire(privateKey, Const.MESSAGE_EXPIRE_DAYS, TimeUnit.DAYS);
            }
            //如果私聊消息不为空，则从Redis中获取
            List<String> messageJsons = stringRedisTemplate.opsForList().range(privateKey, 0, size - 1);
            //解析JSON并转换为消息对象
            List<PrivateChatMessage> messages = new ArrayList<>();
            for (String json : messageJsons) {
                //需要对每一条记录进行判断，如果是list，则转换为list
                if(json.startsWith("[")){
                    List<PrivateChatMessage> message = JSON.parseArray(json, PrivateChatMessage.class);
                    messages.addAll(message);
                }else{
                    messages.add(JSON.parseObject(json, PrivateChatMessage.class));
                }
            }
            //截断私聊消息
            if(messages.size() < limit){
                return messages;
            }
            messages = messages.subList(0, limit);
            return messages;
        } catch (Exception e) {
            logger.error("获取私聊消息失败", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Group_message> getGroupChatHistoryByGroupId(String groupId, int limit) {
        try {
            // 构建群组消息键
            String groupKey = Const.GROUP_CHAT_KEY + groupId;

            // 获取Redis中存储的消息
            Long size = stringRedisTemplate.opsForList().size(groupKey);
            if (size == null || size == 0) {
                //如果群组消息为空，则从数据库中获取
                List<Group_message> groupMessages = group_messageMapper.selectList(Wrappers.<Group_message>query().eq("groupId", groupId));
                //将群组消息保存到Redis中
                stringRedisTemplate.opsForList().rightPush(groupKey, JSON.toJSONString(groupMessages));
                //设置过期时间
                stringRedisTemplate.expire(groupKey, Const.MESSAGE_EXPIRE_DAYS, TimeUnit.DAYS);
            }
            
            // 从Redis获取消息列表
            List<String> messageJsons = stringRedisTemplate.opsForList().range(groupKey, 0, size - 1);
            // 解析JSON并转换为消息对象
            List<Group_message> messages = new ArrayList<>();
            for (String json : messageJsons) {
                try {
                    //由于每一条记录都是list，所以需要转换为list
                    if(json.startsWith("[")){
                        List<Group_message> message = JSON.parseArray(json, Group_message.class);
                        messages.addAll(message);
                    }else{
                        messages.add(JSON.parseObject(json, Group_message.class));
                    }
                } catch (Exception e) {
                    logger.error("解析群组消息JSON失败: {}", json, e);
                }
            }
            
            logger.info("获取到 {} 条群组聊天消息历史, 群组ID: {}", messages.size(), groupId);
            if(messages.size() < limit){
                return messages;
            }
            messages = messages.subList(0, limit);
            return messages;
        } catch (Exception e) {
            logger.error("获取群组聊天历史记录失败", e);
            return new ArrayList<>();
        }
    }
} 