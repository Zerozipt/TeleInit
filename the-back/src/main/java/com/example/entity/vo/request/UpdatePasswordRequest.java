package com.example.entity.vo.request;

import lombok.Data;

@Data
public class UpdatePasswordRequest {
    private String email;
    private String verificationCode;
    private String newPassword;
} 