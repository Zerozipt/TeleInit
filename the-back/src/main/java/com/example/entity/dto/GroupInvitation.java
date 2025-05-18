package com.example.entity.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("group_invitations")
public class GroupInvitation {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("group_id")
    private String groupId;
    
    @TableField("inviter_id")
    private Integer inviterId;
    
    @TableField("invitee_id")
    private Integer inviteeId;
    
    @TableField("status")
    private String status;
    
    @TableField("created_at")
    private Date createdAt;
    
    @TableField("updated_at")
    private Date updatedAt;
} 