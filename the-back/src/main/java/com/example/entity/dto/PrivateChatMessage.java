package com.example.entity.dto;
import lombok.Data;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;


@Data
@TableName("private_messages")
public class PrivateChatMessage {
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
}