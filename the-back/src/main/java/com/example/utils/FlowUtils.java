package com.example.utils;

import org.springframework.beans.factory.annotation.Autowired;
import com.example.service.RedisService;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class FlowUtils {
    @Autowired
    private RedisService redisService;

    //限制请求频率
    public boolean limitOnceCheck(String key, int blockTime) {
        if (redisService.exists(key)) {
            return false;
        }else{
            //如果没有key，设置key的过期时间
            redisService.set(key, "1", java.time.Duration.ofSeconds(blockTime));
            return true;
        }
    }
}
