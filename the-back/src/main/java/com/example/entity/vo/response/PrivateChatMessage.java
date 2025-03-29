package com.example.entity.vo.response;

import java.util.Date;

public class PrivateChatMessage {
    private String content;
    private String fromUser;   // 发送者
    private String toUser;     // 接收者
    private Date timestamp;
    private boolean isRead;    // 是否已读
    
    // 构造方法
    public PrivateChatMessage() {
        this.timestamp = new Date();
        this.isRead = false;
    }
    
    // getters 和 setters
    public String getContent() {
        return content;
    }
    public void setContent(String content) {
        this.content = content;
    }
    public String getFromUser() {
        return fromUser;
    }
    public void setFromUser(String fromUser) {
        this.fromUser = fromUser;
    }
    public String getToUser() {
        return toUser;
    }
    public void setToUser(String toUser) {
        this.toUser = toUser;
    }
    public Date getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
    public boolean isRead() {
        return isRead;
    }
    public void setRead(boolean isRead) {
        this.isRead = isRead;
    }
}