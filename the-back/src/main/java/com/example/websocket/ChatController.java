package com.example.websocket;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import com.example.entity.vo.response.ChatMessage;
import com.example.entity.vo.response.PrivateChatMessage;
// import com.example.service.ChatService; // 移除数据库服务依赖
import org.springframework.beans.factory.annotation.Autowired;
import java.security.Principal;
import java.time.Instant; // 使用 Instant 获取更标准的时间戳
import java.util.Date; // 保留 Date 以便兼容现有 VO，但建议未来统一

@Controller
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    // private final ChatService chatService; // 移除

    @Autowired
    // public ChatController(SimpMessagingTemplate messagingTemplate, ChatService chatService) { // 移除 ChatService
    public ChatController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        // this.chatService = chatService; // 移除
    }

    // 处理公共消息 - 简化为固定频道
    @MessageMapping("/chat/public") // 移除 {groupId}
    @SendTo("/topic/public/general") // 固定到 general 频道
    public ChatMessage handlePublicMessage(@Payload ChatMessage message, // 只接收内容即可，其他由后端填充
                                         Principal principal) {
        // 填充发送者和时间戳
        message.setSender(principal.getName());
        // 使用 Instant 获取 ISO 8601 格式时间戳，更标准
        // 如果 ChatMessage 需要 Date，则转换，否则直接用 String
        message.setTimestamp(Date.from(Instant.now())); // 或者直接设置 String 类型的时间戳

        System.out.println("公共消息: " + message);
        // chatService.savePublicMessage(message); // 移除数据库保存

        return message; // 返回给 @SendTo 处理
    }

    // 处理私人消息 - 简化，不使用路径变量
    @MessageMapping("/chat/private") // 移除 {friendId}
    public void handlePrivateMessage(@Payload PrivateChatMessage message, // 消息体需要包含 toUser 和 content
                                    Principal principal) {
        // 填充发送者和时间戳
        message.setFromUser(principal.getName());
        message.setTimestamp(Date.from(Instant.now())); // 或者 String

        System.out.println("私有消息: 从 " + message.getFromUser() + " 到 " + message.getToUser() + ": " + message.getContent());

        // 验证接收者是否存在 (简单检查，实际应用可能需要查用户服务)
        if (message.getToUser() == null || message.getToUser().trim().isEmpty()) {
             System.err.println("无效的私聊消息：接收者不能为空。");
             // 可以选择性地通知发送者错误，这里仅记录日志
             return;
        }

        // chatService.savePrivateMessage(message); // 移除数据库保存

        // 发送给特定接收用户
        messagingTemplate.convertAndSendToUser(
            message.getToUser(),
            "/queue/private", // 客户端需要订阅 /user/queue/private
            message
        );

        // 同时将消息发回给发送者，实现聊天记录同步
        messagingTemplate.convertAndSendToUser(
            message.getFromUser(),
            "/queue/private",
            message
        );
    }
}