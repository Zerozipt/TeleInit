package com.example.filter;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import com.example.utils.Const;
import jakarta.servlet.http.HttpFilter;
import java.io.IOException;
import jakarta.servlet.ServletException;     
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.example.entity.RestBean;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Component
@Order(Const.ORDER_FLOW_LIMIT)
//流量限制过滤器 - 优化版
//限制单个IP在一秒内的请求次数，超过限制则封禁一段时间
public class FlowLimitFilter extends HttpFilter {   

    @Resource
    StringRedisTemplate stringRedisTemplate;

    // 定义Lua脚本，实现原子化的计数和检查逻辑
    private static final RedisScript<Long> LIMIT_SCRIPT = new DefaultRedisScript<>(
            """
            -- KEYS[1]: 计数器键 (e.g., flow:counter:1.2.3.4)
            -- KEYS[2]: 封禁标记键 (e.g., flow:block:1.2.3.4)
            -- ARGV[1]: 限制次数 (e.g., 20)
            -- ARGV[2]: 封禁时间 (秒, e.g., 60)
            -- ARGV[3]: 计数周期 (秒, e.g., 1)
            
            local counter_key = KEYS[1]
            local block_key = KEYS[2]
            local limit = tonumber(ARGV[1])
            local block_ttl = tonumber(ARGV[2])
            local counter_ttl = tonumber(ARGV[3])
            
            -- 尝试增加计数器
            local count = redis.call('INCR', counter_key)
            
            -- 如果是时间窗口内的第一次请求，设置计数器的过期时间
            if count == 1 then
                redis.call('EXPIRE', counter_key, counter_ttl)
            end
            
            -- 如果计数超过限制
            if count > limit then
                -- 设置封禁标记，并设置过期时间
                redis.call('SET', block_key, '1', 'EX', block_ttl)
                -- 可选：删除计数器键，防止在封禁期间计数器过期
                -- redis.call('DEL', counter_key)
                return 0 -- 返回 0 表示已被阻止
            else
                return 1 -- 返回 1 表示允许请求
            end
            """, Long.class); // 返回值类型为 Long

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {  
        // 对WebSocket请求不进行流量限制
        if (isWebSocketRequest(request)) {
            chain.doFilter(request, response);
            return;
        }
        
        String ip = getClientIp(request);
        if(tryCount(ip)){
            chain.doFilter(request, response);
        }else{
            this.writeBlockMessage(response);
        }
    }
    //如果是websocket请求，则不进行流量限制
    // 判断是否为WebSocket请求
    private boolean isWebSocketRequest(HttpServletRequest request) {
        // 检查请求路径是否为WebSocket端点
        String requestURI = request.getRequestURI();
        if (requestURI != null) {
            // 匹配WebSocket主端点和SockJS相关端点
            if (requestURI.contains("/ws-chat")) {
                return true;
            }
            // 匹配SockJS的轮询请求
            if (requestURI.matches(".*\\/ws-chat\\/\\d+\\/.*")) {
                return true;
            }
            // 匹配SockJS的info请求
            if (requestURI.contains("/ws-chat/info")) {
                return true;
            }
        }
        
        // 检查请求头是否包含WebSocket相关信息
        String upgrade = request.getHeader("Upgrade");
        String connection = request.getHeader("Connection");
        return "websocket".equalsIgnoreCase(upgrade) || 
               (connection != null && connection.toLowerCase().contains("upgrade"));
    }

    private void writeBlockMessage(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write(RestBean.forbidden("请求过于频繁，请稍后再试").asJsonString());
    }

    
    private boolean tryCount(String ip){
        //使用ip.intern()来减少内存占用
        if(Boolean.TRUE.equals(stringRedisTemplate.hasKey(Const.FLOW_LIMIT_BLOCK + ":" + ip))){
            return false;
        }
        return this.limitPeriodCheck(ip);
    }

    private boolean limitPeriodCheck(String ip){
        String counterKey = Const.FLOW_LIMIT_COUNTER + ":" + ip;
        String blockKey = Const.FLOW_LIMIT_BLOCK + ":" + ip;
        try {
            // 执行Lua脚本
            Long result = stringRedisTemplate.execute(
                    LIMIT_SCRIPT,
                    List.of(counterKey, blockKey), // KEYS列表
                    String.valueOf(Const.FLOW_LIMIT_COUNT), // ARGV[1]: 限制次数
                    String.valueOf(60),                     // ARGV[2]: 封禁时间 (秒)
                    String.valueOf(1)                      // ARGV[3]: 计数周期 (秒)
            );
            // 脚本返回 1 表示允许，0 表示阻止 (或者 null/异常)
            return result != null && result == 1L;
        } catch (Exception e) {
            // Redis 操作失败时的处理策略：可以选择放行或阻止
            // 这里选择放行，但记录错误日志非常重要
            // TODO: 替换为你的日志框架，例如 SLF4j
            System.err.println("Redis flow limit check failed for IP " + ip + ": " + e.getMessage());
            return true; // 发生异常时，暂时放行请求
        }
    }

    // 尝试获取真实客户端IP，优先检查 X-Forwarded-For 和 X-Real-IP
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For 可能包含多个IP，取第一个
            String[] ips = xForwardedFor.split(",");
            String clientIp = ips[0].trim();
            // 简单的IP格式校验 (可能需要更严格的校验)
             if (!clientIp.isEmpty() && clientIp.matches("^[\\da-fA-F.:]+$")) {
                 return clientIp;
            }
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
             if (xRealIp.matches("^[\\da-fA-F.:]+$")) {
                 return xRealIp;
            }
        }

        // 回退到 remoteAddr
        return request.getRemoteAddr();
    }
}

