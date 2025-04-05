package com.example.entity.dto;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("group_messages")
public class Group_message {
    private String groupId;
    private String content;
    private String sender_id;
    //数据库的时间类型是timestamp,对应的java类型是Date
    private Date create_at;
    private short content_type;
    private String file_url;
}
