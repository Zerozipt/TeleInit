package com.example.service;

import java.time.Duration;

public interface OnlineStatusService {
    void markOnline(String userId, Duration ttl);
    boolean isOnline(String userId);
    boolean refreshOnline(String userId, Duration ttl);
    void markOffline(String userId);
} 