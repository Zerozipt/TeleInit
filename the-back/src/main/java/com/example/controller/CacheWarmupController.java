package com.example.controller;

import com.example.entity.dto.CacheWarmupTask;
import com.example.service.CacheWarmupService;
import com.example.utils.JwtUtils;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;

/**
 * 缓存预热管理控制器
 * 提供预热任务的监控和管理接口
 */
@RestController
@RequestMapping("/api/cache-warmup")
public class CacheWarmupController {
    
    private static final Logger logger = LoggerFactory.getLogger(CacheWarmupController.class);
    
    @Autowired
    private CacheWarmupService cacheWarmupService;
    
    @Resource
    private JwtUtils jwtUtils;
    
    /**
     * 获取预热任务统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getWarmupStats(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "用户未认证"));
            }
            
            CacheWarmupService.WarmupTaskStats stats = cacheWarmupService.getTaskStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("stats", stats);
            response.put("message", "获取预热任务统计成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取预热任务统计失败", e);
            return ResponseEntity.status(500).body(Map.of("error", "获取统计信息失败"));
        }
    }
    
    /**
     * 手动触发预热任务处理
     */
    @PostMapping("/process")
    public ResponseEntity<?> processPendingTasks(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            if (userDetails == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "用户未认证"));
            }
            
            if (limit <= 0 || limit > 100) {
                return ResponseEntity.badRequest().body(Map.of("error", "limit必须在1-100之间"));
            }
            
            int processed = cacheWarmupService.processPendingTasks(limit);
            
            Map<String, Object> response = new HashMap<>();
            response.put("processedCount", processed);
            response.put("message", String.format("手动处理预热任务完成，处理数量: %d", processed));
            
            logger.info("手动触发预热任务处理: 用户={}, 处理数量={}", userDetails.getUsername(), processed);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("手动处理预热任务失败", e);
            return ResponseEntity.status(500).body(Map.of("error", "处理预热任务失败"));
        }
    }
    
    /**
     * 重试失败的预热任务
     */
    @PostMapping("/retry")
    public ResponseEntity<?> retryFailedTasks(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            if (userDetails == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "用户未认证"));
            }
            
            if (limit <= 0 || limit > 50) {
                return ResponseEntity.badRequest().body(Map.of("error", "limit必须在1-50之间"));
            }
            
            int retried = cacheWarmupService.retryFailedTasks(3, limit);
            
            Map<String, Object> response = new HashMap<>();
            response.put("retriedCount", retried);
            response.put("message", String.format("重试失败任务完成，重试数量: %d", retried));
            
            logger.info("手动重试失败预热任务: 用户={}, 重试数量={}", userDetails.getUsername(), retried);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("重试失败预热任务失败", e);
            return ResponseEntity.status(500).body(Map.of("error", "重试预热任务失败"));
        }
    }
    
    /**
     * 清理旧的预热任务
     */
    @DeleteMapping("/cleanup")
    public ResponseEntity<?> cleanupOldTasks(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "7") int days) {
        try {
            if (userDetails == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "用户未认证"));
            }
            
            if (days <= 0 || days > 30) {
                return ResponseEntity.badRequest().body(Map.of("error", "days必须在1-30之间"));
            }
            
            int deleted = cacheWarmupService.cleanupOldTasks(days);
            
            Map<String, Object> response = new HashMap<>();
            response.put("deletedCount", deleted);
            response.put("message", String.format("清理旧预热任务完成，删除数量: %d", deleted));
            
            logger.info("手动清理旧预热任务: 用户={}, 保留天数={}, 删除数量={}", 
                       userDetails.getUsername(), days, deleted);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("清理旧预热任务失败", e);
            return ResponseEntity.status(500).body(Map.of("error", "清理预热任务失败"));
        }
    }
    
    /**
     * 手动创建预热任务
     */
    @PostMapping("/create")
    public ResponseEntity<?> createWarmupTask(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody CreateWarmupTaskRequest request) {
        try {
            if (userDetails == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "用户未认证"));
            }
            
            if (request == null || request.getCacheType() == null || 
                request.getCacheKey() == null || request.getEntityId() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "请求参数不完整"));
            }
            
            CacheWarmupTask task = cacheWarmupService.createWarmupTask(
                request.getCacheType(),
                request.getCacheKey(),
                request.getEntityId(),
                request.getPriority() != null ? request.getPriority() : CacheWarmupTask.Priority.MEDIUM
            );
            
            if (task != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("task", task);
                response.put("message", "预热任务创建成功");
                
                logger.info("手动创建预热任务: 用户={}, 任务ID={}", userDetails.getUsername(), task.getId());
                
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "预热任务已存在或创建失败"));
            }
            
        } catch (Exception e) {
            logger.error("创建预热任务失败", e);
            return ResponseEntity.status(500).body(Map.of("error", "创建预热任务失败"));
        }
    }
    
    /**
     * 创建预热任务的请求体
     */
    public static class CreateWarmupTaskRequest {
        private String cacheType;
        private String cacheKey;
        private String entityId;
        private Integer priority;
        
        // getters and setters
        public String getCacheType() { return cacheType; }
        public void setCacheType(String cacheType) { this.cacheType = cacheType; }
        
        public String getCacheKey() { return cacheKey; }
        public void setCacheKey(String cacheKey) { this.cacheKey = cacheKey; }
        
        public String getEntityId() { return entityId; }
        public void setEntityId(String entityId) { this.entityId = entityId; }
        
        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }
    }
} 