package com.example.entity.vo.request;

import java.security.Principal;

public class CustomPrincipal implements Principal {  
    private final String userId;  // 用户唯一ID  
    private final String username; // 用户名（可能重复）  

    public CustomPrincipal(String userId, String username) {  
        this.userId = userId;  
        this.username = username;  
    }  

    @Override  
    public String getName() {  
        // 这里返回的是username，但我们同时有userId  
        return userId;  
    }  

    public String getUserId() {  
        return userId;  
    }  

    public String getUsername() {  
        return username;  
    }  

    @Override  
    public String toString() {  
        return "CustomPrincipal{userId=" + userId + ", username='" + username + "'}";  
    }  
}  