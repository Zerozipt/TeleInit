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
    // 枚举定义消息类型
    public enum MessageType {
        CHAT, JOIN, LEAVE, NOTICE
    }
    
    // 构造方法
    public ChatMessage() {
        this.timestamp = new Date();
    }
    
    // getters 和 setters
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    // 其他getter/setter...
    public String getContent() {
        return content;
    }
    public void setContent(String content) {
        this.content = content;
    }
    public String getSender() {
        return sender;
    }
    public void setSender(String sender) {
        this.sender = sender;
    }
    public String getRoomId() {
        return roomId;
    }
    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }
    public Date getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
}