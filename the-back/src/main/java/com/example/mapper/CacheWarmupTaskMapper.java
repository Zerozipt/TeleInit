package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.dto.CacheWarmupTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CacheWarmupTaskMapper extends BaseMapper<CacheWarmupTask> {
    
    /**
     * 查询待处理的预热任务（按优先级和创建时间排序）
     * @param limit 限制数量
     * @return 待处理任务列表
     */
    @Select("SELECT * FROM cache_warmup_tasks WHERE status = 'PENDING' " +
            "ORDER BY priority ASC, created_at ASC LIMIT #{limit}")
    List<CacheWarmupTask> findPendingTasks(@Param("limit") int limit);
    
    /**
     * 查询失败但可重试的预热任务
     * @param maxRetryCount 最大重试次数
     * @param limit 限制数量
     * @return 可重试任务列表
     */
    @Select("SELECT * FROM cache_warmup_tasks WHERE status = 'FAILED' " +
            "AND retry_count < #{maxRetryCount} " +
            "ORDER BY priority ASC, created_at ASC LIMIT #{limit}")
    List<CacheWarmupTask> findRetryableTasks(@Param("maxRetryCount") int maxRetryCount, 
                                           @Param("limit") int limit);
    
    /**
     * 根据缓存键查询任务（避免重复创建）
     * @param cacheKey 缓存键
     * @return 任务列表
     */
    @Select("SELECT * FROM cache_warmup_tasks WHERE cache_key = #{cacheKey} " +
            "AND status IN ('PENDING', 'PROCESSING') ORDER BY created_at DESC LIMIT 1")
    CacheWarmupTask findByCacheKey(@Param("cacheKey") String cacheKey);
    
    /**
     * 查询指定时间内的任务统计
     * @param hours 小时数
     * @return 任务数量
     */
    @Select("SELECT COUNT(*) FROM cache_warmup_tasks " +
            "WHERE created_at >= DATE_SUB(NOW(), INTERVAL #{hours} HOUR)")
    int countRecentTasks(@Param("hours") int hours);
    
    /**
     * 清理已完成的旧任务
     * @param days 保留天数
     * @return 删除的任务数量
     */
    @Select("DELETE FROM cache_warmup_tasks " +
            "WHERE status = 'COMPLETED' AND processed_at < DATE_SUB(NOW(), INTERVAL #{days} DAY)")
    int cleanupCompletedTasks(@Param("days") int days);
} 