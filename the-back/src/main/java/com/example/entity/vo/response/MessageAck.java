package com.example.entity.vo.response;

import lombok.Data;
import java.util.Date;

@Data
public class MessageAck {
    private String tempId;      // 前端发送的临时ID
    private String realId;      // 数据库中的真实消息ID
    private boolean success;    // 是否成功保存到数据库
    private String error;       // 错误信息（失败时）
    private Date timestamp;     // 确认时间戳
    private String messageType; // 消息类型（private/group）
    
    // 静态工厂方法
    public static MessageAck success(String tempId, String realId, String messageType) {
        MessageAck ack = new MessageAck();
        ack.setTempId(tempId);
        ack.setRealId(realId);
        ack.setSuccess(true);
        ack.setMessageType(messageType);
        ack.setTimestamp(new Date());
        return ack;
    }
    
    public static MessageAck failure(String tempId, String error, String messageType) {
        MessageAck ack = new MessageAck();
        ack.setTempId(tempId);
        ack.setSuccess(false);
        ack.setError(error);
        ack.setMessageType(messageType);
        ack.setTimestamp(new Date());
        return ack;
    }
} 