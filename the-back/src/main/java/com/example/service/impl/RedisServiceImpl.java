package com.example.service.impl;

import com.example.service.RedisService;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class RedisServiceImpl implements RedisService {

    private static final Logger logger = LoggerFactory.getLogger(RedisServiceImpl.class);

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redisTemplate;

    @Override
    public void set(String key, String value, Duration ttl) {
        try {
            if (ttl != null && !ttl.isZero()) {
                redisTemplate.opsForValue().set(key, value, ttl.toMillis(), TimeUnit.MILLISECONDS);
            } else {
                redisTemplate.opsForValue().set(key, value);
            }
        } catch (Exception e) {
            logger.error("Error setting value for key: " + key, e);
        }
    }

    @Override
    public String get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            logger.error("Error getting value for key: " + key, e);
            return null;
        }
    }

    @Override
    public boolean delete(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.delete(key));
        } catch (Exception e) {
            logger.error("Error deleting key: " + key, e);
            return false;
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            logger.error("Error checking existence of key: " + key, e);
            return false;
        }
    }

    @Override
    public void pushList(String key, String value, int maxLen, Duration ttl) {
        try {
            redisTemplate.opsForList().rightPush(key, value);
            if (maxLen > 0) {
                // 保留最新 maxLen 条
                redisTemplate.opsForList().trim(key, -maxLen, -1);
            }
            if (ttl != null && !ttl.isZero()) {
                redisTemplate.expire(key, ttl.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            logger.error("Error pushing to list for key: " + key, e);
        }
    }

    @Override
    public List<String> rangeList(String key, long start, long end) {
        try {
            List<String> list = redisTemplate.opsForList().range(key, start, end);
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            logger.error("Error getting range from list for key: " + key, e);
            return Collections.emptyList();
        }
    }

    @Override
    public void addToZSet(String key, String member, double score, Duration ttl) {
        try {
            redisTemplate.opsForZSet().add(key, member, score);
            if (ttl != null && !ttl.isZero()) {
                redisTemplate.expire(key, ttl.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            logger.error("Error adding to ZSet for key: " + key, e);
        }
    }

    @Override
    public Set<String> rangeByScore(String key, double min, double max) {
        try {
            Set<String> set = redisTemplate.opsForZSet().rangeByScore(key, min, max);
            return set != null ? set : Collections.emptySet();
        } catch (Exception e) {
            logger.error("Error getting range by score for key: " + key, e);
            return Collections.emptySet();
        }
    }

    @Override
    public void hashIncrement(String key, String field, long delta, Duration ttl) {
        try {
            redisTemplate.opsForHash().increment(key, field, delta);
            if (ttl != null && !ttl.isZero()) {
                redisTemplate.expire(key, ttl.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            logger.error("Error incrementing hash for key: " + key, e);
        }
    }

    @Override
    public <T> T executeScript(RedisScript<T> script, List<String> keys, Object... args) {
        try {
            return redisTemplate.execute(script, keys, args);
        } catch (Exception e) {
            logger.error("Error executing script", e);
            return null;
        }
    }
} 