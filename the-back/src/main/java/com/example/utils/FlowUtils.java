package com.example.utils;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class FlowUtils {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    //限制请求频率
    public boolean limitOnceCheck(String key, int blockTime) {
        //判断是否存在key
        if(Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            return false;
        }else{
            //如果没有key，设置key的过期时间
            stringRedisTemplate.opsForValue().set(key, "1", blockTime,TimeUnit.SECONDS);
            return true;
        }
    }
}
