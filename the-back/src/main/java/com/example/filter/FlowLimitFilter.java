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
import java.util.concurrent.TimeUnit;
import java.util.Optional;


@Component
@Order(Const.ORDER_FLOW_LIMIT)
//流量限制过滤器
//当用户一秒超过20次请求，直接封禁ip60秒
public class FlowLimitFilter extends HttpFilter {   

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {  
        // 对WebSocket请求不进行流量限制
        if (isWebSocketRequest(request)) {
            chain.doFilter(request, response);
            return;
        }
        
        String ip = request.getRemoteAddr();
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
        response.getWriter().write(RestBean.forbidden("请求过于频繁").asJsonString());
    }

    
    private boolean tryCount(String ip){
        //使用ip.intern()来减少内存占用
        synchronized (ip.intern()){
            if(Boolean.TRUE.equals(stringRedisTemplate.hasKey(Const.FLOW_LIMIT_BLOCK + ":" + ip))){
                return false;
            }
            return this.limitPeriodCheck(ip);
        }
    }

    private boolean limitPeriodCheck(String ip){
        if(Boolean.TRUE.equals(stringRedisTemplate.hasKey(Const.FLOW_LIMIT_COUNTER + ":" + ip))){
            Long increment = Optional.ofNullable(stringRedisTemplate.opsForValue().increment(Const.FLOW_LIMIT_COUNTER + ":" + ip)).orElse(0L);
            if(increment > Const.FLOW_LIMIT_COUNT){
                stringRedisTemplate.opsForValue().set(Const.FLOW_LIMIT_BLOCK + ":" + ip, "", 60, TimeUnit.SECONDS);
                return false;
            }
        }else{
            stringRedisTemplate.opsForValue().set(Const.FLOW_LIMIT_COUNTER + ":" + ip, "1", 1, TimeUnit.SECONDS);
        }
        return true;
    }
}

