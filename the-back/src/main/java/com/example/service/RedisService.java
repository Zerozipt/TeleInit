package com.example.service;

import org.springframework.data.redis.core.script.RedisScript;
import java.time.Duration;
import java.util.List;
import java.util.Set;

public interface RedisService {
    // Value operations
    void set(String key, String value, Duration ttl);
    String get(String key);
    boolean delete(String key);
    boolean exists(String key);

    // List operations with optional length control
    void pushList(String key, String value, int maxLen, Duration ttl);
    List<String> rangeList(String key, long start, long end);

    // Sorted Set operations
    void addToZSet(String key, String member, double score, Duration ttl);
    Set<String> rangeByScore(String key, double min, double max);

    // Hash increment
    void hashIncrement(String key, String field, long delta, Duration ttl);

    // Execute Lua script or other scripts
    <T> T executeScript(RedisScript<T> script, List<String> keys, Object... args);
} 