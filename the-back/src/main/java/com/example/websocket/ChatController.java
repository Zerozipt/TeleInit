package com.example.websocket;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;   
import com.example.entity.vo.response.ChatMessage;
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
import com.example.entity.dto.PrivateChatMessage;
import com.example.entity.vo.response.FriendsResponse;
import com.example.entity.dto.Group_message;
import java.util.stream.Collectors;
import com.example.utils.ConvertUtils;
import java.util.ArrayList;
import java.util.Map;
import com.example.entity.vo.response.StatusMessage;
import com.example.service.OnlineStatusService;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.service.ChatCacheService;
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AccountServiceImpl accountServiceImpl;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    @Resource
    private ChatService chatService;

    @Resource
    private JwtUtils jwtUtils;

    @Autowired
    private OnlineStatusService onlineStatusService;

    @Autowired
    private ChatCacheService chatCacheService;

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

    // 修改方法签名，接受 Object (或 ChatMessage)
    public void sendMessageToGroup(String groupId, Object messagePayload) {
        String destination = "/topic/channel/" + groupId;
        // 使用 JSON.toJSONString 打印，避免直接 toString 可能不清晰
        System.out.println("发送消息到群组: " + destination + ", 消息内容: " + JSON.toJSONString(messagePayload));
        // 发送完整的对象，SimpMessagingTemplate 会自动处理 JSON 转换
        messagingTemplate.convertAndSend(destination, messagePayload);
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
            List<FriendsResponse> friendIds = chatService.getFriends(userId);

            List<FriendsResponse> friendRequests = chatService.getFriendRequests(userId);

            List<Group_member> groupIds = chatService.getGroups(userId);
 
            List<List<Group_message>> groupMessages = groupIds.stream().map(groupId -> chatService.getGroupChatHistoryByGroupId(groupId.getGroupId(), 100)).collect(Collectors.toList());
   
            List<PrivateChatMessage> privateMessages = chatService.getPrivateChatHistory(Integer.parseInt(userId), 100);
            // 将私聊消息转换为包含用户名的 JSON 对象列表
            List<JSONObject> privateMessagesWithNames = new ArrayList<>();
            for (PrivateChatMessage msg : privateMessages) {
                JSONObject m = new JSONObject();
                m.put("id", msg.getId());
                m.put("senderId", msg.getSenderId());
                m.put("senderName", accountServiceImpl.getAccountById(msg.getSenderId()).getUsername());
                m.put("receiverId", msg.getReceiverId());
                m.put("receiverName", accountServiceImpl.getAccountById(msg.getReceiverId()).getUsername());
                m.put("content", msg.getContent());
                m.put("isRead", msg.isRead());
                m.put("createdAt", msg.getCreatedAt());
                m.put("fileUrl", msg.getFileUrl());
                m.put("fileName", msg.getFileName());
                m.put("fileType", msg.getFileType());
                m.put("fileSize", msg.getFileSize());
                m.put("messageType", msg.getMessageType());
                
                if (msg.getMessageType() == null && msg.getFileUrl() != null && !msg.getFileUrl().isEmpty()) {
                    String messageType = "FILE";
                    if (msg.getFileType() != null) {
                        if (msg.getFileType().startsWith("image/")) {
                            messageType = "IMAGE";
                        } else if (msg.getFileType().startsWith("video/")) {
                            messageType = "VIDEO";
                        } else if (msg.getFileType().startsWith("audio/")) {
                            messageType = "AUDIO"; 
                        }
                    }
                    m.put("messageType", messageType);
                }
                privateMessagesWithNames.add(m);
            }
            
            // 处理群组消息，确保文件元数据完整
            for (List<Group_message> messages : groupMessages) {
                for (Group_message msg : messages) {
                    if (msg.getMessageType() == null) {
                        if (msg.getFileUrl() != null && !msg.getFileUrl().isEmpty()) {
                            if (msg.getContentType() == 1) {
                                msg.setMessageType("IMAGE");
                            } else if (msg.getContentType() == 2) {
                                msg.setMessageType("FILE");
                            } else {
                                msg.setMessageType("FILE"); // 默认为文件类型
                            }
                        } else {
                            msg.setMessageType("TEXT"); // 没有文件URL则为文本消息
                        }
                    }
                }
            }
            //构建返回message，包括用户id，用户名，用户的好友关系和群聊关系，要求返回的格式为json
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("userId", userId);
            jsonObject.put("username", decodedJWT.getClaim("name").asString());
            jsonObject.put("friendIds", JSON.toJSONString(friendIds));
            jsonObject.put("friendRequests", JSON.toJSONString(friendRequests));
            jsonObject.put("groupIds", JSON.toJSONString(groupIds));
            jsonObject.put("groupMessages", JSON.toJSONString(groupMessages));
            jsonObject.put("privateMessages", JSON.toJSONString(privateMessagesWithNames));
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
    @MessageMapping("/chat/channel")
    public void handlePublicMessage(@Payload ChatMessage message, CustomPrincipal principal) {
        System.out.println("群组消息: " + message);
        // 填充发送者和时间戳
        message.setSenderId(Integer.parseInt(principal.getName()));
        message.setSender(principal.getUsername());
        message.setTimestamp(Date.from(Instant.now()));
        
        // 检查文件消息并设置正确的消息类型
        if (message.getFileUrl() != null && !message.getFileUrl().isEmpty() && message.getMessageType() == null) {
            message.setMessageType("FILE");
        }
        
        // 缓存群组消息
        String groupId = message.getGroupId() != null ? message.getGroupId() : "default";
        rabbitTemplate.convertAndSend("groupChat", message);
        chatCacheService.cacheGroupMessage(groupId, JSON.toJSONString(message));
        // 传递完整的 message 对象
        this.sendMessageToGroup(groupId, message);
        
        // 群组消息也要单独发给发送者自己，确保UI显示一致
        messagingTemplate.convertAndSendToUser(
            principal.getName(),
            "/queue/channel",
            message
        );
    }

    // 处理私人消息 - 简化，不使用路径变量
    @MessageMapping("/chat/private")
    public void handlePrivateMessage(@Payload ChatMessage message, CustomPrincipal principal) {
        // 填充发送者信息和时间戳
        message.setSender(principal.getUsername());
        message.setSenderId(Integer.parseInt(principal.getName()));
        message.setTimestamp(Date.from(Instant.now()));

        if (message.getReceiverId() == null) {
            System.err.println("无效的私聊消息：接收者不能为空。");
            return;
        }
        
        // 异步持久化和 Redis 操作由 PrivateChatMessageListener 及 savePrivateMessage 处理
        rabbitTemplate.convertAndSend("privateChat", message);
        
        // 实时推送给接收者
        messagingTemplate.convertAndSendToUser(
            message.getReceiverId().toString(),
            "/queue/private",
            message
        );
        
        // 新增：对于文件消息，也推送给发送者自己以便在UI上显示
        // 检查是否是文件消息
        boolean isFileMessage = false;
        if (message.getMessageType() != null && 
           (message.getMessageType().equalsIgnoreCase("FILE") || 
            message.getMessageType().equalsIgnoreCase("IMAGE") || 
            message.getMessageType().equalsIgnoreCase("VIDEO") || 
            message.getMessageType().equalsIgnoreCase("AUDIO"))) {
            isFileMessage = true;
        } else if (message.getFileUrl() != null && !message.getFileUrl().isEmpty()) {
            isFileMessage = true;
            // 如果没有指定消息类型但有文件URL，默认设为FILE类型
            if (message.getMessageType() == null) {
                message.setMessageType("FILE");
            }
        }
        
        // 文件消息或者普通消息都要发给自己
        messagingTemplate.convertAndSendToUser(
            principal.getName(),
            "/queue/private",
            message
        );
        
        System.out.println("发送" + (isFileMessage ? "文件" : "文本") + "消息: " + message);
    }

    // 处理心跳包
    @MessageMapping("/chat/heartbeat")
    @SendTo("/topic/public/general")
    public void handleHeartbeat(Principal principal) {
        if (principal == null) {
            return;
        }
        
        // 更新用户在线状态
        String userId = principal.getName();
        boolean refreshed = onlineStatusService.refreshOnline(userId, Duration.ofSeconds(30));
        if (refreshed) {
            System.out.println("用户 " + userId + " 心跳，在线状态已刷新");
        } else {
            // 如果状态不存在或刷新失败，重新设置
            onlineStatusService.markOnline(userId, Duration.ofSeconds(30));
            System.out.println("用户 " + userId + " 心跳，在线状态已设置");
        }
        
        // 返回心跳包，前端收到心跳包后更新
        messagingTemplate.convertAndSendToUser(
            principal.getName(),
            "/queue/heartbeat",
            "heartbeat"
        );
    }

 

    // 处理系统消息
    @MessageMapping("/system/radiate")
    public void handleSystemMessage(Principal principal, Map<String, Object> message) {
        System.out.println("收到系统消息: " + message);
    }

    @MessageMapping("/system/offline")
    public void handleOffline(Principal principal, StatusMessage message) {
        System.out.println("收到离线消息: " + principal.getName());
        
        // 标记离线
        String userId = message.getUserId();
        onlineStatusService.markOffline(userId);
        System.out.println("用户 " + userId + " 下线，在线状态已删除");
        
        // 通知好友用户已下线
        List<FriendsResponse> friends = chatService.getFriends(message.getUserId());
        for (FriendsResponse friend : friends) {
            if(friend.getSecondUserId().equals(message.getUserId())){
                messagingTemplate.convertAndSendToUser(
                    friend.getFirstUserId(),
                    "/queue/offline",
                    message
                );
            }
            else if(friend.getFirstUserId().equals(message.getUserId())){
                messagingTemplate.convertAndSendToUser(
                    friend.getSecondUserId(),
                    "/queue/offline",
                    message
                );
            }
        }
    }

    @MessageMapping("/system/online")
    public void handleOnline(Principal principal, StatusMessage message) {
        System.out.println("收到上线消息: " + message);
        // 使用 principal 获取用户 ID，确保不为空
        String userId = principal.getName();
        message.setUserId(userId);
        // 标记上线
        onlineStatusService.markOnline(userId, Duration.ofSeconds(30));
        System.out.println("用户 " + userId + " 上线，在线状态已设置");
        // 通知所有好友
        List<FriendsResponse> friends = chatService.getFriends(userId);
        for (FriendsResponse friend : friends) {
            String targetId = friend.getSecondUserId().equals(userId)
                    ? friend.getFirstUserId()
                    : friend.getSecondUserId();
            System.out.println("发送上线消息给: " + targetId);
            messagingTemplate.convertAndSendToUser(
                    targetId,
                    "/queue/online",
                    message
            );
        }
    }

    @GetMapping("/history/private")
    public RestBean<JSONObject> getPrivateChatHistory(@RequestParam String userId, @RequestParam String friendId, @RequestParam String oldestMessageId) {
        List<PrivateChatMessage> privateMessages = chatService.getPrivateChatHistoryByUserIdAndFriendId(Integer.parseInt(userId), Integer.parseInt(friendId), 100, oldestMessageId);
        JSONObject jsonObject = new JSONObject();
        // 将私聊消息转换为包含用户名的 JSON 对象列表
        List<JSONObject> privateMessagesWithNames = new ArrayList<>();
        for (PrivateChatMessage msg : privateMessages) {
            JSONObject m = new JSONObject();
            m.put("id", msg.getId());
            m.put("senderId", msg.getSenderId());
            m.put("senderName", accountServiceImpl.getAccountById(msg.getSenderId()).getUsername());
            m.put("receiverId", msg.getReceiverId());
            m.put("receiverName", accountServiceImpl.getAccountById(msg.getReceiverId()).getUsername());
            m.put("content", msg.getContent());
            m.put("isRead", msg.isRead());
            m.put("createdAt", msg.getCreatedAt());
            m.put("fileUrl", msg.getFileUrl());
            m.put("fileName", msg.getFileName());
            m.put("fileType", msg.getFileType());
            m.put("fileSize", msg.getFileSize());
            m.put("messageType", msg.getMessageType());
            
            if (msg.getMessageType() == null && msg.getFileUrl() != null && !msg.getFileUrl().isEmpty()) {
                String messageType = "FILE";
                if (msg.getFileType() != null) {
                    if (msg.getFileType().startsWith("image/")) {
                        messageType = "IMAGE";
                    } else if (msg.getFileType().startsWith("video/")) {
                        messageType = "VIDEO";
                    } else if (msg.getFileType().startsWith("audio/")) {
                        messageType = "AUDIO"; 
                    }
                }
                m.put("messageType", messageType);
            }
            privateMessagesWithNames.add(m);
        }
        jsonObject.put("privateMessages", JSON.toJSONString(privateMessagesWithNames));
        return messageHandler(() -> jsonObject);
    }
    
    // 新增：获取群聊消息历史
    @GetMapping("/history/group")
    public RestBean<List<Group_message>> getGroupChatHistory(@RequestParam String groupId, @RequestParam int limit) {
        List<Group_message> groupMessages = chatService.getGroupChatHistoryByGroupId(groupId, limit);
        
        groupMessages.forEach(msg -> {
            if (msg.getMessageType() == null) {
                if (msg.getFileUrl() != null && !msg.getFileUrl().isEmpty()) {
                    if (msg.getContentType() == 1) {
                        msg.setMessageType("IMAGE");
                    } else if (msg.getContentType() == 2) {
                        msg.setMessageType("FILE");
                    } else {
                        msg.setMessageType("FILE");
                    }
                } else {
                    msg.setMessageType("TEXT");
                }
            }
        });
        
        return RestBean.success(groupMessages);
    }
}