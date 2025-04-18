package com.example.entity.vo.response;

import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatMessage {
    private MessageType type;  // 消息类型(枚举)
    private String content;    // 消息内容
    private String sender;     // 发送者用户名
    private String roomId;     // 聊天室/频道ID (可选)
    private Date timestamp;    // 时间戳
    private String groupId;    // 频道ID
    private Integer senderId;  // 发送者ID
    // 枚举定义消息类型
    public enum MessageType {
        CHAT, JOIN, LEAVE, NOTICE
    }

}