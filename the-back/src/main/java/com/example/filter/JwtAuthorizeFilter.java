package com.example.filter;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.utils.JwtUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    /**
     * 过滤器核心逻辑，用于处理每个请求的授权验证。
     * @param request HTTP请求
     * @param response HTTP响应
     * @param filterChain 过滤器链
     * @throws ServletException 如果发生Servlet异常
     * @throws IOException 如果发生IO异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                HttpServletResponse response,
                                FilterChain filterChain)
        throws ServletException, IOException {
    // 从请求头中获取Authorization信息
    String authorization = request.getHeader("Authorization");
    // 解析JWT令牌
    DecodedJWT jwt = jwtUtils.resolveJWT(authorization);
    if (jwt != null) {
        // 将JWT转换为UserDetails对象
        UserDetails user = jwtUtils.toUser(jwt);
        // 创建UsernamePasswordAuthenticationToken并设置认证信息
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        // 设置认证细节
        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        // 设置当前安全上下文的认证信息
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        // 将用户ID设置到请求属性中
        request.setAttribute("id", jwtUtils.tpId(jwt));
    }
    // 继续过滤器链
    filterChain.doFilter(request, response);
    }
}