package com.example.websocket;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import com.example.entity.vo.response.ChatMessage;
import com.example.entity.vo.response.PrivateChatMessage;
import com.example.service.impl.AccountServiceImpl;
import com.example.service.ChatService;
import com.example.entity.vo.request.CustomPrincipal;
import java.security.Principal;
import java.time.Instant; // 使用 Instant 获取更标准的时间戳
import java.util.Date; // 保留 Date 以便兼容现有 VO，但建议未来统一
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import jakarta.annotation.Resource;
import com.example.utils.Const;
import java.util.concurrent.TimeUnit;
import com.alibaba.fastjson2.JSON;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.example.entity.RestBean;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.utils.JwtUtils;
import java.util.List;
import com.alibaba.fastjson2.JSONObject;
import java.util.function.Supplier;
import org.springframework.web.bind.annotation.RestController;
import com.example.entity.dto.Friends;
import com.example.entity.dto.Group_member;
import com.example.entity.vo.response.FriendsResponse;
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AccountServiceImpl accountServiceImpl;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private  SimpMessagingTemplate messagingTemplate;
    // private final ChatService chatService; // 移除

    @Resource
    private ChatService chatService;

    @Resource
    private JwtUtils jwtUtils;


    ChatController(AccountServiceImpl accountServiceImpl) {
        this.accountServiceImpl = accountServiceImpl;
    }


    // 处理消息的函数,如果消息不为空，则返回成功，否则返回失败 
    private RestBean<JSONObject> messageHandler(Supplier<JSONObject> action){
        JSONObject message = action.get();
        if (message != null) {
            return RestBean.success(message);
        } else {
            return RestBean.failure(400, "消息为空");
        }
    }

    public void sendMessageToGroup(String groupId, String message) {  
        int channelId = Math.abs(groupId.hashCode() % 100);  
        String destination = "/topic/channel" + channelId;  
        messagingTemplate.convertAndSend(destination, message);  
    } 

    // 这个方法现在只从Authorization头中获取JWT
    @PostMapping("/GetThePrivateMessage")
    public RestBean<JSONObject> GetThePrivateMessage(
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            // 从Authorization头中提取JWT
            String jwt = null;
            if (authorization != null && authorization.startsWith("Bearer ")) {
                jwt = authorization.substring(7);
            }
            
            if (jwt == null) {
                return RestBean.failure(401, "未提供JWT令牌");
            }
            
            //解析jwt
            DecodedJWT decodedJWT = jwtUtils.resolveJWTFromLocalStorage(jwt);
            String userId = decodedJWT.getClaim("id").asString();
            //通过用户id,构建一个返回message，包括用户id，用户名，用户的好友关系和群聊关系
            //从redis中获取用户的好友关系和群聊关系,转化成json
            List<FriendsResponse> friendIds = chatService.getFriends(userId);
            //从redis中获取群聊关系
            List<Group_member> groupIds = chatService.getGroups(userId);
            //构建返回message，包括用户id，用户名，用户的好友关系和群聊关系，要求返回的格式为json
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("userId", userId);
            jsonObject.put("username", decodedJWT.getClaim("name").asString());
            jsonObject.put("friendIds", JSON.toJSONString(friendIds));
            jsonObject.put("groupIds", JSON.toJSONString(groupIds));
            System.out.println("返回的message: " + jsonObject);
            return messageHandler(() -> jsonObject);
        } catch (Exception e) {
            System.err.println("获取用户信息错误: " + e.getMessage());
            e.printStackTrace();
            //返回一个空json
            return messageHandler(() -> new JSONObject());
        }
    }
    
    // 处理公共消息 - 简化为固定频道
    // 逻辑是，用户发送的消息都发送到一个频道，然后前端根据频道id订阅对应的频道
    // 然后后端根据频道id，将消息发送到对应的频道，可行性的原因是订阅频道是可以动态注册的
    @MessageMapping("/chat/channel") // 移除 {groupId}
    public void handlePublicMessage(@Payload ChatMessage message, // 只接收内容即可，其他由后端填充
                                         CustomPrincipal principal) {
        // 填充发送者和时间戳
        message.setSender(principal.getName());
        message.setSender(principal.getUserId());
        // 使用 Instant 获取 ISO 8601 格式时间戳，更标准
        // 如果 ChatMessage 需要 Date，则转换，否则直接用 String
        message.setTimestamp(Date.from(Instant.now())); // 或者直接设置 String 类型的时间戳
        //从message中获取groupId
        String groupId = message.getGroupId();
        System.out.println("群组消息: " + message);
        // chatService.savePublicMessage(message); // 移除数据库保存
        //将消息发送到群聊队列,进行消息的持久化,先发送到队列，再保存到redis
        rabbitTemplate.convertAndSend("groupChat", message);
        String key = Const.GROUP_CHAT_KEY + (message.getGroupId() != null ? message.getGroupId() : "default");
        // 将消息转为JSON保存
        stringRedisTemplate.opsForList().rightPush(key, JSON.toJSONString(message));
        // 设置过期时间
        stringRedisTemplate.expire(key, Const.MESSAGE_EXPIRE_DAYS, TimeUnit.DAYS);
        this.sendMessageToGroup(groupId, message.getContent());
    }

    // 处理私人消息 - 简化，不使用路径变量
    @MessageMapping("/chat/private") // 移除 {friendId}
    public void handlePrivateMessage(@Payload PrivateChatMessage message, // 消息体需要包含 toUser 和 content
                                    CustomPrincipal principal) {
        // 填充发送者和时间戳
        message.setFromUser(principal.getUsername());
        message.setFromUserId(principal.getUserId());
        message.setTimestamp(Date.from(Instant.now())); // 或者 String

        // System.out.println("私有消息: 从 " + message.getFromUserId() + " 到 " + message.getToUserId() + ": " + message.getContent());
        // 验证接收者是否存在 (简单检查，实际应用可能需要查用户服务)
        if (message.getToUserId() == null || message.getToUserId().trim().isEmpty()) {
             System.err.println("无效的私聊消息：接收者不能为空。");
             // 可以选择性地通知发送者错误，这里仅记录日志
             return;
        }
        
        //将消息发送到私聊队列,进行消息的持久化,先发送到队列，再保存到redis
        rabbitTemplate.convertAndSend("privateChat", message);
        
        // 保存消息到Redis
        // 使用一致的消息键格式，确保发送者和接收者看到相同的历史记录
        chatService.savePrivateMessage(message);
        
        // 发送消息给接收者
        messagingTemplate.convertAndSendToUser(
            message.getToUserId(),
            "/queue/messages", // 客户端需要订阅 /user/queue/messages
            message
        );
        
        // 同时将消息发回给发送者，实现聊天记录同步
        messagingTemplate.convertAndSendToUser(
            message.getFromUserId(),
            "/queue/messages",
            message
        );
    }

    // 处理心跳包
    @MessageMapping("/chat/heartbeat")
    @SendTo("/topic/public/general")
    public void handleHeartbeat(Principal principal) {
        System.out.println("收到心跳包: " + principal.getName());
        // 返回心跳包 前端收到心跳包后，更新心跳包的时间
        messagingTemplate.convertAndSendToUser(
            principal.getName(),
            "/queue/heartbeat",
            "heartbeat"
        );
    }
    
    /**
     * 获取私聊消息历史
     * @param user1 第一个用户名
     * @param user2 第二个用户名
     * @param limit 限制返回消息数量，默认50条
     * @return 包含私聊消息列表的响应
     */
    @GetMapping("/history/private")
    public RestBean<List<PrivateChatMessage>> getPrivateChatHistory(
            @RequestParam String user1,
            @RequestParam String user2,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization) {
        
        try {
            // 验证认证
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return RestBean.failure(401, "用户未认证");
            }
            
            String jwt = authorization.substring(7);
            DecodedJWT decodedJWT = jwtUtils.resolveJWTFromLocalStorage(jwt);
            String currentUsername = decodedJWT.getClaim("name").asString();
            
            // 安全检查: 只允许查询自己参与的聊天
            if (!currentUsername.equals(user1) && !currentUsername.equals(user2)) {
                return RestBean.failure(403, "无权查看其他用户的聊天记录");
            }
            
            // 获取聊天记录
            List<PrivateChatMessage> chatHistory = chatService.getPrivateChatHistory(user1, user2, limit);
            return RestBean.success(chatHistory);
        } catch (Exception e) {
            e.printStackTrace();
            return RestBean.failure(500, "获取聊天记录失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取群聊消息历史
     * @param groupId 群组ID
     * @param limit 限制返回消息数量，默认50条
     * @return 包含群聊消息列表的响应
     */
    @GetMapping("/history/group")
    public RestBean<List<ChatMessage>> getGroupChatHistory(
            @RequestParam String groupId,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization) {
        
        try {
            // 验证认证
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return RestBean.failure(401, "用户未认证");
            }
            
            String jwt = authorization.substring(7);
            DecodedJWT decodedJWT = jwtUtils.resolveJWTFromLocalStorage(jwt);
            String userId = decodedJWT.getClaim("id").asString();
            
            // 安全检查: 验证用户是群成员
            List<Group_member> userGroups = chatService.getGroups(userId);
            boolean isMember = userGroups.stream()
                    .anyMatch(group -> group.getGroup_id().equals(groupId));
            
            if (!isMember) {
                return RestBean.failure(403, "非群成员无法查看群聊记录");
            }
            
            // 获取聊天记录
            List<ChatMessage> chatHistory = chatService.getGroupChatHistory(groupId, limit);
            return RestBean.success(chatHistory);
        } catch (Exception e) {
            e.printStackTrace();
            return RestBean.failure(500, "获取群聊记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取私聊消息历史（与前端路径完全匹配）
     */
    @GetMapping("/chat/history/private")
    public RestBean<List<PrivateChatMessage>> getPrivateChatHistoryCompatible(
            @RequestParam String user1,
            @RequestParam String user2,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization) {
        return getPrivateChatHistory(user1, user2, limit, authorization);
    }
    
    /**
     * 获取群聊消息历史（与前端路径完全匹配）
     */
    @GetMapping("/chat/history/group")
    public RestBean<List<ChatMessage>> getGroupChatHistoryCompatible(
            @RequestParam String groupId,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization) {
        return getGroupChatHistory(groupId, limit, authorization);
    }
}