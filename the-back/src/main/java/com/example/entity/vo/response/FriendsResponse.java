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
    private boolean isOnline;
    
    public enum Status {
        requested, //请求
        accepted, //接受
        rejected, //拒绝
        deleted //删除
    }
}
