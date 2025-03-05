package com.example.config;

import com.example.entity.RestBean;
import com.example.entity.vo.response.AuthorizeVO;
import com.example.filter.JwtAuthorizeFilter;
import com.example.utils.JwtUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.io.PrintWriter;

@Configuration
public class SecurityConfiguration {

    @Resource
    JwtUtils jwtUtils;

    @Resource
    JwtAuthorizeFilter filter;

    /**
     * 配置Spring Security的安全过滤链。
     * @param http HttpSecurity实例
     * @param jwtAuthorizeFilter JWT授权过滤器
     * @return 安全过滤链
     * @throws Exception 如果配置过程中发生异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthorizeFilter jwtAuthorizeFilter) throws Exception {
        return http
                .authorizeHttpRequests(conf -> conf
                        .requestMatchers("/api/auth/**").permitAll() // 允许所有对/api/auth/**的请求
                        .anyRequest().authenticated() // 其他所有请求需要认证
                )
                .formLogin(conf -> conf
                        .loginProcessingUrl("/api/auth/login") // 登录处理URL
                        .successHandler(this::onAuthenticationSuccess) // 认证成功处理器
                        .failureHandler(this::onAuthenticationFailure) // 认证失败处理器
                )
                .logout(conf -> conf
                        .logoutUrl("/api/auth/logout") // 注销URL
                        .logoutSuccessHandler(this::onLogoutSuccess) // 注销成功处理器
                )
                .exceptionHandling(conf -> conf
                        .authenticationEntryPoint(this::onUnauthorized) // 未授权处理器
                        .accessDeniedHandler(this::onAccessDeny) // 访问被拒绝处理器
                )
                .csrf(AbstractHttpConfigurer::disable) // 禁用CSRF保护
                .sessionManagement(conf -> conf
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // 无状态会话管理
                )
                .addFilterBefore(jwtAuthorizeFilter, UsernamePasswordAuthenticationFilter.class) // 添加JWT授权过滤器
                .build();
    }

    /**
     * 处理访问被拒绝的情况。
     * @param request HTTP请求
     * @param response HTTP响应
     * @param exception 访问被拒绝异常
     * @throws IOException 如果发生IO异常
     */
    public void onAccessDeny(HttpServletRequest request,
                             HttpServletResponse response,
                             AccessDeniedException exception) throws IOException {
        response.setContentType("application/json;charset=utf-8"); // 设置响应内容类型为JSON
        response.getWriter().write(RestBean.forbidden(exception.getMessage()).asJsonString()); // 返回禁止访问的响应
    }

    /**
     * 处理认证成功的情况。
     * @param request HTTP请求
     * @param response HTTP响应
     * @param authentication 认证信息
     * @throws IOException 如果发生IO异常
     * @throws ServletException 如果发生Servlet异常
     */
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        response.setContentType("application/json;charset=utf-8"); // 设置响应内容类型为JSON
        User user = (User) authentication.getPrincipal(); // 获取认证用户信息
        String token = jwtUtils.CreateJWT(user, 1, "MrTest"); // 创建JWT令牌
        AuthorizeVO vo = new AuthorizeVO(); // 创建授权响应对象
        vo.setExpire(jwtUtils.expireTime()); // 设置令牌过期时间
        vo.setRole(""); // 设置角色信息
        vo.setToken(token); // 设置JWT令牌
        vo.setUsername("MrTest"); // 设置用户名
        response.getWriter().write(RestBean.success(vo).asJsonString()); // 返回成功的响应
    }

    /**
     * 处理未授权的情况。
     * @param request HTTP请求
     * @param response HTTP响应
     * @param exception 认证异常
     * @throws IOException 如果发生IO异常
     * @throws SecurityException 如果发生安全异常
     */
    public void onUnauthorized(HttpServletRequest request,
                               HttpServletResponse response,
                               AuthenticationException exception) throws IOException, SecurityException {
        response.setContentType("application/json;charset=utf-8"); // 设置响应内容类型为JSON
        response.getWriter().write(RestBean.unauthorized(exception.getMessage()).asJsonString()); // 返回未授权的响应
    }

    /**
     * 处理认证失败的情况。
     * @param request HTTP请求
     * @param response HTTP响应
     * @param exception 认证异常
     * @throws IOException 如果发生IO异常
     * @throws SecurityException 如果发生安全异常
     */
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, SecurityException {
        response.setContentType("application/json;charset=utf-8"); // 设置响应内容类型为JSON
        response.getWriter().write(RestBean.unauthorized(exception.getMessage()).asJsonString()); // 返回未授权的响应
    }

    /**
     * 处理注销成功的情况。
     * @param request HTTP请求
     * @param response HTTP响应
     * @param authentication 认证信息
     * @throws IOException 如果发生IO异常
     */
    public void onLogoutSuccess(HttpServletRequest request,
                                HttpServletResponse response,
                                Authentication authentication) throws IOException {
        response.setContentType("application/json;charset=utf-8"); // 设置响应内容类型为JSON
        PrintWriter writer = response.getWriter(); // 获取PrintWriter对象
        String authorization = request.getHeader("Authorization"); // 获取Authorization头

        // 检查是否提供了授权头
        if (authorization == null || authorization.isEmpty()) {
            writer.write(RestBean.failure(400, "未提供令牌").asJsonString()); // 返回未提供令牌的错误响应
            return;
        }

        // 直接使用invalidateJWT方法 - 它已经处理了Bearer前缀
        if (jwtUtils.invalidateJWT(authorization)) {
            writer.write(RestBean.success().asJsonString()); // 返回成功的响应
        } else {
            writer.write(RestBean.failure(400, "退出登陆失败").asJsonString()); // 返回退出登录失败的错误响应
        }
    }
}