package com.example.controller;

import com.example.entity.RestBean;
import com.example.entity.dto.Account;
import com.example.service.AccountService;
import jakarta.annotation.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Resource
    private AccountService accountService;

    /**
     * 搜索用户
     * @param term 搜索关键词
     * @param userDetails 当前认证用户信息
     * @return 用户列表
     */
    @GetMapping("/search")
    public RestBean<List<Map<String, Object>>> searchUsers(
            @RequestParam String term,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return RestBean.failure(401, "用户未认证");
            }

            int currentUserId = accountService.findIdByUsername(userDetails.getUsername());
            if (currentUserId <= 0) {
                return RestBean.failure(500, "无法获取当前用户ID");
            }

            // 搜索用户
            List<Account> users = accountService.searchUsers(term);
            
            // 转换为前端需要的格式，并排除当前用户
            List<Map<String, Object>> result = users.stream()
                .filter(user -> user.getId() != currentUserId)
                .map(user -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", String.valueOf(user.getId()));
                    map.put("username", user.getUsername());
                    map.put("email", user.getEmail());
                    map.put("avatar", "/api/placeholder/80/80"); // 使用占位头像
                    return map;
                })
                .collect(Collectors.toList());
                
            return RestBean.success(result);
        } catch (Exception e) {
            return RestBean.failure(500, "搜索用户失败: " + e.getMessage());
        }
    }
} 