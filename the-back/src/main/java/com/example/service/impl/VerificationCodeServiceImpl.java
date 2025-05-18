package com.example.service.impl;

import com.example.service.VerificationCodeService;
import com.example.service.RedisService;
import com.example.utils.RedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {

    @Autowired
    private RedisService redisService;

    @Override
    public void saveEmailCode(String email, String code, Duration ttl) {
        String key = RedisKeys.VERIFY_EMAIL + email;
        redisService.set(key, code, ttl);
    }

    @Override
    public String getEmailCode(String email) {
        String key = RedisKeys.VERIFY_EMAIL + email;
        return redisService.get(key);
    }

    @Override
    public void deleteEmailCode(String email) {
        String key = RedisKeys.VERIFY_EMAIL + email;
        redisService.delete(key);
    }
} 