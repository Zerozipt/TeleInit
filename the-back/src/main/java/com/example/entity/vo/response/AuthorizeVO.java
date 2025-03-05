package com.example.entity.vo.response;

import lombok.Data;

import java.util.Date;

@Data
public class AuthorizeVO {
    String username;
    String role;
    String token;
    Date expire;

    //fastjson和lombok不兼容，原因未知，需要手动setter和getter
    public void setUsername(String username){
        this.username = username;
    }
    public void setRole(String role){
        this.role = role;
    }
    public void setToken(String token){
        this.token = token;
    }
    public void setExpire(Date expire){
        this.expire = expire;
    }
    public String getUsername(){
        return username;
    }
    public String getRole(){
        return role;
    }
    public String getToken(){
        return token;
    }
    public Date getExpire(){
        return expire;
    }
}
