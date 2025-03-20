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
        String ip = request.getRemoteAddr();
        if(tryCount(ip)){
            chain.doFilter(request, response);
        }else{
            this.writeBlockMessage(response);
        }
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
            stringRedisTemplate.opsForValue().set(Const.FLOW_LIMIT_COUNTER + ":" + ip, "1", 3, TimeUnit.SECONDS);
        }
        return true;
    }
}

