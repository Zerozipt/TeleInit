package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.dto.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {
    
    /**
     * 查询待处理的事件
     * @param limit 限制数量
     * @return 待处理事件列表
     */
    @Select("SELECT * FROM outbox_events WHERE status = 'PENDING' " +
            "ORDER BY created_at ASC LIMIT #{limit}")
    List<OutboxEvent> findPendingEvents(@Param("limit") int limit);
    
    /**
     * 查询失败但可重试的事件
     * @param maxRetryCount 最大重试次数
     * @param limit 限制数量
     * @return 可重试事件列表
     */
    @Select("SELECT * FROM outbox_events WHERE status = 'FAILED' " +
            "AND retry_count < #{maxRetryCount} " +
            "ORDER BY created_at ASC LIMIT #{limit}")
    List<OutboxEvent> findRetryableEvents(@Param("maxRetryCount") int maxRetryCount, 
                                         @Param("limit") int limit);
    
    /**
     * 根据实体ID和事件类型查询事件
     * @param entityId 实体ID
     * @param eventType 事件类型
     * @return 事件列表
     */
    @Select("SELECT * FROM outbox_events WHERE entity_id = #{entityId} " +
            "AND event_type = #{eventType} ORDER BY created_at DESC")
    List<OutboxEvent> findByEntityIdAndEventType(@Param("entityId") String entityId,
                                                 @Param("eventType") String eventType);
} 