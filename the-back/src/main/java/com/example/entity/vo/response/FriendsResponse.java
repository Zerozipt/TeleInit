package com.example.entity.vo.response;

import java.util.Date;

import lombok.Data;

@Data
public class FriendsResponse {
    private String firstUserId;
    private String secondUserId;
    private String firstUsername;
    private String secondUsername;
    private Date created_at;
    private Status status;
    public enum Status {
        requested,
        accepted,
        deleted
    }
}
