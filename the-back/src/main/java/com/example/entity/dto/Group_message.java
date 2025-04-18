package com.example.entity.dto;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("group_messages")
public class Group_message {  // 类名建议使用大驼峰命名
    private Long id;         // 添加主键字段
    @TableField("groupId")
    private String groupId;   // 使用驼峰命名
    //content在数据库中是text类型，所以需要使用String类型
    private String content;
    @TableField("SenderId")
    private Integer SenderId;  // 改用驼峰命名
    @TableField("Create_at")
    private Date CreateAt;    // 改用驼峰命名
    @TableField("Content_type")
    private Short ContentType; // 包装类型更安全
    @TableField("File_url")
    private String FileUrl;
}
