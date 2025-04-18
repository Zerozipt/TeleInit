package com.example.entity.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.util.Date;

@Data
@TableName("group_members")
public class Group_member {
    @TableField("user_id")
    private Integer userId;
    @TableField("group_id")
    private String groupId;
    @TableField("joined_at")
    private Date joinedAt;
    @TableField("role")
    private String role;
    @TableField("groupname")
    private String groupName;
}
