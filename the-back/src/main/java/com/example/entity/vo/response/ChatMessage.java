package com.example.entity.vo.response;

import java.util.Date;
import lombok.Data;

@Data
public class ChatMessage {
    //消息字段是否可选取决于消息类型
    private String id;         // 消息ID（数据库中的真实ID）
    private MessageType type;  // 消息类型(枚举)(群聊消息，私聊消息，系统消息)(可选)
    private String content;    // 消息内容
    private String sender;     // 发送者用户名
    private String roomId;     // 聊天室/频道ID (可选)
    private Date timestamp;    // 时间戳
    private String groupId;    // 群组ID(可选)
    private Integer senderId;  // 发送者ID
    private Integer receiverId; // 接收者ID(可选)
    private String fileUrl;    // 文件URL(可选)
    private String messageType; // 前端发送的更具体的消息类型 (例如 "FILE", "IMAGE", "TEXT")
    private String fileName;   // 文件原始名称，用于下载时提供文件名
    private String fileType;   // 文件MIME类型，如image/jpeg, application/pdf等
    private Long fileSize;     // 文件大小（字节数）
    private String tempId;     // 前端生成的临时ID，用于消息确认机制
    
    // 枚举定义消息类型
    public enum MessageType {
        CHAT, JOIN, LEAVE, NOTICE
    }

}