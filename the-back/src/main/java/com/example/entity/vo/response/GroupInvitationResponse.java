package com.example.entity.vo.response;

import lombok.Data;
import java.util.Date;

@Data
public class GroupInvitationResponse {
    private Long id;
    private String groupId;
    private String groupName;
    private Integer inviterId;
    private String inviterName;
    private Integer inviteeId;
    private String inviteeName;
    private String status;
    private Date createdAt;
} 