package com.example.service.impl;

import com.example.service.OnlineStatusService;
import com.example.service.RedisService;
import com.example.utils.RedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class OnlineStatusServiceImpl implements OnlineStatusService {

    @Autowired
    private RedisService redisService;

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(30);

    @Override
    public void markOnline(String userId, Duration ttl) {
        Duration effectiveTtl = ttl != null ? ttl : DEFAULT_TTL;
        redisService.set(RedisKeys.ONLINE_STATUS + userId, "1", effectiveTtl);
    }

    @Override
    public boolean isOnline(String userId) {
        return redisService.exists(RedisKeys.ONLINE_STATUS + userId);
    }

    @Override
    public boolean refreshOnline(String userId, Duration ttl) {
        String key = RedisKeys.ONLINE_STATUS + userId;
        if (redisService.exists(key)) {
            Duration effectiveTtl = ttl != null ? ttl : DEFAULT_TTL;
            // 刷新 TTL
            redisService.set(key, "1", effectiveTtl);
            return true;
        }
        return false;
    }

    @Override
    public void markOffline(String userId) {
        redisService.delete(RedisKeys.ONLINE_STATUS + userId);
    }
} 