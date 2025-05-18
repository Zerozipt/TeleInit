package com.example.service;

import java.time.Duration;

public interface VerificationCodeService {
    void saveEmailCode(String email, String code, Duration ttl);
    String getEmailCode(String email);
    void deleteEmailCode(String email);
} 