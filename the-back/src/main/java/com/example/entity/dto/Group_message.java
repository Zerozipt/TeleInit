package com.example.entity.dto;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
@Data
@TableName("group_messages")
public class Group_message {  // 类名建议使用大驼峰命名
    @TableId(type = IdType.AUTO, value = "id")
    private Long id;         // 添加主键字段
    @TableField("groupId")
    private String groupId;   // 使用驼峰命名
    //content在数据库中是text类型，所以需要使用String类型
    private String content;
    @TableField("SenderId")
    private Integer SenderId;  // 改用驼峰命名

    @TableField(exist = false) // 此字段不在数据库表中，用于业务逻辑
    private String senderName; // 新增发送者用户名字段

    @TableField("Create_at")
    private Date CreateAt;    // 改用驼峰命名
    @TableField("Content_type")
    private Short ContentType; // 包装类型更安全
    @TableField("File_url")
    private String FileUrl;
    
    @TableField("File_name")
    private String FileName;
    
    @TableField("File_type")
    private String FileType;
    
    @TableField("File_size")
    private Long FileSize;
    
    @TableField(exist = false)
    private String MessageType;
}
