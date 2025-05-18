package com.example.entity.vo.request;

import lombok.Data;

@Data
public class GroupInvitationRequest {
    private String groupId;
    private Integer inviteeId;
} 