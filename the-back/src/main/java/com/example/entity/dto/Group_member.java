package com.example.entity.dto;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.util.Date;

@Data
@TableName("group_members")
public class Group_member {
    private Integer user_id;
    private String group_id;
    private Date joined_at;
    private String role;
    private String groupname;
}
