package com.example.entity.dto;
import lombok.Data;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;

@Data
@TableName("private_messages")
public class PrivateChatMessage {
    @TableId(type = IdType.AUTO, value = "id")
    private Long id;
    @TableField("sender_id")
    private int senderId;
    @TableField("receiver_id")
    private int receiverId;
    private String content;
    private boolean isRead;
    @TableField("created_at")
    private Date createdAt;
    @TableField("file_url")
    private String fileUrl;
    @TableField("file_name")
    private String fileName;
    @TableField("file_type")
    private String fileType;
    @TableField("file_size")
    private Long fileSize;
    @TableField("message_type")
    private String messageType;
}