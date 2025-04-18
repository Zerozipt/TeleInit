package com.example.entity.dto;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("group_messages")
public class Group_message {  // 类名建议使用大驼峰命名
    private Long id;         // 添加主键字段
    private String groupId;   // 使用驼峰命名
    private String content;
    private String Sender;  // 改用驼峰命名
    private Date Create_at;    // 改用驼峰命名
    private Short Content_type; // 包装类型更安全
    private String File_url;
}
