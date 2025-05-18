package com.example.entity.vo.response;

import lombok.Data;
import java.util.Date;

@Data
public class GroupMemberResponse {
    private Integer userId;
    private String username;
    private String avatar; 
    private String role;
    private Date joinedAt;
} 