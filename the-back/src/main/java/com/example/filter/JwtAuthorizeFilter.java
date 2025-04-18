package com.example.filter;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.utils.JwtUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthorizeFilter extends OncePerRequestFilter {

    @Resource
    JwtUtils jwtUtils;

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthorizeFilter.class); // 使用 Logger

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String requestURI = request.getRequestURI(); // 获取请求 URI
        logger.debug("Processing request URI: {}", requestURI); // 打印请求 URI

        String authorization = request.getHeader("Authorization");
        logger.debug("Authorization Header: {}", authorization); // 打印 Header

        if (authorization != null && authorization.startsWith("Bearer ")) { // 确保 Header 存在且以 Bearer 开头
            try {
                DecodedJWT jwt = jwtUtils.resolveJWT(authorization); // 尝试解析和验证
                logger.debug("JWT resolved: {}", (jwt != null ? "Success" : "Failed or Invalid")); // 打印解析结果

                if (jwt != null) {
                    UserDetails user = jwtUtils.toUser(jwt);
                    logger.debug("UserDetails created: {}", (user != null ? user.getUsername() : "null")); // 打印 UserDetails

                    if (user != null) { // 确保 UserDetails 创建成功
                        UsernamePasswordAuthenticationToken authenticationToken =
                                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                        logger.debug("Authentication set in SecurityContext for user: {}", user.getUsername()); // 确认设置成功

                        request.setAttribute("id", jwtUtils.tpId(jwt)); // 这行可以保留
                    } else {
                        logger.warn("Failed to create UserDetails from JWT for URI: {}", requestURI);
                    }
                } else {
                    // 如果 resolveJWT 返回 null，意味着 Token 无效或验证失败
                    logger.warn("JWT validation failed or token is invalid/blacklisted for URI: {}", requestURI);
                    // 注意：这里不需要显式返回 401，后续过滤器会处理 SecurityContext 为空的情况
                }
            } catch (Exception e) {
                // 如果 resolveJWT 或 toUser 抛出异常
                logger.error("Error processing JWT token for URI: {}", requestURI, e);
                // 同样，让后续过滤器处理认证失败
            }
        } else {
            logger.debug("No Bearer token found in Authorization Header for URI: {}", requestURI);
        }

        // 无论认证是否成功，都必须调用 filterChain.doFilter
        filterChain.doFilter(request, response);
        logger.debug("Filter chain continued for URI: {}", requestURI); // 确认过滤器链继续
    }
}