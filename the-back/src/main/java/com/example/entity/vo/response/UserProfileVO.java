package com.example.entity.vo.response;

import lombok.Data;
import java.util.Date;

@Data
public class UserProfileVO {
    private Integer id;
    private String username;
    private String email;
    private String role;
    private Date registerTime;
    private String avatar;
    
    // 手动添加getter和setter方法，避免Lombok兼容性问题
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    
    public Date getRegisterTime() { return registerTime; }
    public void setRegisterTime(Date registerTime) { this.registerTime = registerTime; }
    
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
} 