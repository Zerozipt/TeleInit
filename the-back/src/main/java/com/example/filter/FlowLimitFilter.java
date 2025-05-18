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
import org.springframework.beans.factory.annotation.Autowired;
import com.example.service.RateLimiterService;


@Component
@Order(Const.ORDER_FLOW_LIMIT)
//流量限制过滤器 - 优化版
//限制单个IP在一秒内的请求次数，超过限制则封禁一段时间
public class FlowLimitFilter extends HttpFilter {   

    @Autowired
    private RateLimiterService rateLimiterService;

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {  
        // 对WebSocket请求不进行流量限制
        if (isWebSocketRequest(request)) {
            chain.doFilter(request, response);
            return;
        }
        
        String ip = getClientIp(request);
        if (rateLimiterService.tryDefault(ip)) {
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

