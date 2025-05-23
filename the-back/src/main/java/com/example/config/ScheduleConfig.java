package com.example.config;

import com.example.service.OutboxEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 定时任务配置，用于处理本地消息表事件
 */
@Configuration
@EnableScheduling
public class ScheduleConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(ScheduleConfig.class);
    
    @Autowired
    private OutboxEventService outboxEventService;
    
    /**
     * 每30秒处理一次待处理的事件
     * outbox_events表已创建，可以启用
     */
    @Scheduled(fixedDelay = 30000) // 30秒
    public void processPendingEvents() {
        try {
            int processed = outboxEventService.processPendingEvents(50); // 每次处理最多50个事件
            if (processed > 0) {
                logger.info("处理了 {} 个待处理事件", processed);
            }
        } catch (Exception e) {
            logger.error("处理待处理事件时发生错误", e);
        }
    }
    
    /**
     * 每2分钟重试一次失败的事件
     * outbox_events表已创建，可以启用
     */
    @Scheduled(fixedDelay = 120000) // 2分钟
    public void retryFailedEvents() {
        try {
            int retried = outboxEventService.retryFailedEvents(3, 20); // 最多重试3次，每次处理20个
            if (retried > 0) {
                logger.info("重试了 {} 个失败事件", retried);
            }
        } catch (Exception e) {
            logger.error("重试失败事件时发生错误", e);
        }
    }
} 