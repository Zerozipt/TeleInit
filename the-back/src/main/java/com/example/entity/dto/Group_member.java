package com.example.entity.dto;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("group_members")
public class Group_member {
  
    private String user_id;

    private String group_id;

    private String joined_at;

    private String role;
    
}
