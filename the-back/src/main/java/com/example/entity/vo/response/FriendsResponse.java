package com.example.entity.vo.response;

import java.util.Date;

import lombok.Data;

@Data
public class FriendsResponse {
    private String userId;
    private String username;
    private Date created_at;
}
