package com.example.service.impl;

import com.example.service.RateLimiterService;
import com.example.service.RedisService;
import com.example.utils.Const;
import com.example.utils.RedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RateLimiterServiceImpl implements RateLimiterService {

    @Autowired
    private RedisService redisService;

    private static final RedisScript<Long> LIMIT_SCRIPT = new DefaultRedisScript<>(
            """
            -- KEYS[1]: counter key
            -- KEYS[2]: block key
            -- ARGV[1]: limit count
            -- ARGV[2]: block ttl (sec)
            -- ARGV[3]: window ttl (sec)
            local counter = KEYS[1]
            local block = KEYS[2]
            local limit = tonumber(ARGV[1])
            local block_ttl = tonumber(ARGV[2])
            local window = tonumber(ARGV[3])
            local count = redis.call('INCR', counter)
            if count == 1 then
                redis.call('EXPIRE', counter, window)
            end
            if count > limit then
                redis.call('SET', block, '1', 'EX', block_ttl)
                return 0
            else
                return 1
            end
            """,
            Long.class);

    @Override
    public boolean tryOnce(String key, int blockSeconds) {
        String fullKey = RedisKeys.RATE_LIMIT_ONCE + key;
        if (redisService.exists(fullKey)) {
            return false;
        }
        redisService.set(fullKey, "1", java.time.Duration.ofSeconds(blockSeconds));
        return true;
    }

    @Override
    public boolean tryWindow(String id, int limitCount, int blockSeconds, int windowSeconds) {
        String counterKey = RedisKeys.RATE_LIMIT_WINDOW_COUNTER + id;
        String blockKey = RedisKeys.RATE_LIMIT_WINDOW_BLOCK + id;
        Long result = redisService.executeScript(
                LIMIT_SCRIPT,
                List.of(counterKey, blockKey),
                String.valueOf(limitCount),
                String.valueOf(blockSeconds),
                String.valueOf(windowSeconds)
        );
        return result != null && result == 1L;
    }

    @Override
    public boolean tryDefault(String id) {
        return tryWindow(id, Const.FLOW_LIMIT_COUNT, 60, 1);
    }
} 