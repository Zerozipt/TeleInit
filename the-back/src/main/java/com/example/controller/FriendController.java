package com.example.controller;

import com.example.entity.RestBean;
import com.example.entity.dto.Account;
import com.example.entity.dto.Friends;
import com.example.service.AccountService;
import com.example.service.ChatService;
import jakarta.annotation.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    @Resource
    private AccountService accountService;

    @Resource
    private ChatService chatService;

    /**
     * 发送好友请求
     * @param request 包含目标用户ID的请求体
     * @param userDetails 当前认证用户信息
     * @return 操作结果
     */
    @PostMapping("/request")
    public RestBean<Map<String, Object>> sendFriendRequest(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return RestBean.failure(401, "用户未认证");
            }

            // 获取当前用户ID
            int currentUserId = accountService.findIdByUsername(userDetails.getUsername());
            if (currentUserId <= 0) {
                return RestBean.failure(500, "无法获取当前用户ID");
            }

            // 从请求中获取目标用户ID
            Integer targetUserId = null;
            // 兼容两种参数名和类型
            if (request.containsKey("targetUserId")) {
                // 目标ID可能是整数或字符串
                Object rawTargetId = request.get("targetUserId");
                if (rawTargetId instanceof Integer) {
                    targetUserId = (Integer) rawTargetId;
                } else if (rawTargetId instanceof String) {
                    try {
                        targetUserId = Integer.parseInt((String) rawTargetId);
                    } catch (NumberFormatException e) {
                        return RestBean.failure(400, "无效的目标用户ID格式");
                    }
                }
            } else if (request.containsKey("username")) {
                // 兼容通过用户名添加的方式
                String targetUsername = (String) request.get("username");
                targetUserId = accountService.findIdByUsername(targetUsername);
            }

            if (targetUserId == null || targetUserId <= 0) {
                return RestBean.failure(400, "无效的目标用户");
            }

            // 检查是否尝试加自己为好友
            if (currentUserId == targetUserId) {
                return RestBean.failure(400, "不能添加自己为好友");
            }

            // 检查是否已经是好友
            if (chatService.isFriend(currentUserId, targetUserId)) {
                return RestBean.failure(409, "已经是好友关系");
            }

            // 检查是否已经发送过好友请求
            if (chatService.hasFriendRequest(currentUserId, targetUserId)) {
                return RestBean.failure(409, "已经发送过好友请求");
            }

            // 创建好友请求
            Friends friendRequest = new Friends();
            friendRequest.setTheFirstUserId(currentUserId);
            friendRequest.setTheSecondUserId(targetUserId);
            friendRequest.setStatus(Friends.Status.requested);
            friendRequest.setCreatedAt(new java.sql.Timestamp(System.currentTimeMillis()));

            // 保存好友请求
            chatService.saveFriendRequest(friendRequest);

            return RestBean.success(Map.of(
                "message", "好友请求已发送",
                "requestId", friendRequest.getId()
            ));

        } catch (Exception e) {
            return RestBean.failure(500, "发送好友请求失败: " + e.getMessage());
        }
    }

    /**
     * 获取收到的好友请求列表
     * @param userDetails 当前认证用户信息
     * @return 好友请求列表
     */
    @GetMapping("/requests/received")
    public RestBean<List<Map<String, Object>>> getReceivedFriendRequests(
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return RestBean.failure(401, "用户未认证");
            }

            int currentUserId = accountService.findIdByUsername(userDetails.getUsername());
            if (currentUserId <= 0) {
                return RestBean.failure(500, "无法获取当前用户ID");
            }

            List<Map<String, Object>> requests = chatService.getReceivedFriendRequests(currentUserId);
            return RestBean.success(requests);
        } catch (Exception e) {
            return RestBean.failure(500, "获取好友请求失败: " + e.getMessage());
        }
    }

    /**
     * 接受好友请求
     * @param requestId 请求ID
     * @param userDetails 当前认证用户信息
     * @return 操作结果
     */
    @PostMapping("/requests/{requestId}/accept")
    public RestBean<Void> acceptFriendRequest(
            @PathVariable int requestId,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return RestBean.failure(401, "用户未认证");
            }

            int currentUserId = accountService.findIdByUsername(userDetails.getUsername());
            if (currentUserId <= 0) {
                return RestBean.failure(500, "无法获取当前用户ID");
            }

            boolean result = chatService.acceptFriendRequest(requestId, currentUserId);
            if (result) {
                return RestBean.success();
            } else {
                return RestBean.failure(404, "好友请求不存在或无权操作");
            }
        } catch (Exception e) {
            return RestBean.failure(500, "接受好友请求失败: " + e.getMessage());
        }
    }

    /**
     * 拒绝好友请求
     * @param requestId 请求ID
     * @param userDetails 当前认证用户信息
     * @return 操作结果
     */
    @PostMapping("/requests/{requestId}/reject")
    public RestBean<Void> rejectFriendRequest(
            @PathVariable int requestId,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return RestBean.failure(401, "用户未认证");
            }

            int currentUserId = accountService.findIdByUsername(userDetails.getUsername());
            if (currentUserId <= 0) {
                return RestBean.failure(500, "无法获取当前用户ID");
            }

            boolean result = chatService.rejectFriendRequest(requestId, currentUserId);
            if (result) {
                return RestBean.success();
            } else {
                return RestBean.failure(404, "好友请求不存在或无权操作");
            }
        } catch (Exception e) {
            return RestBean.failure(500, "拒绝好友请求失败: " + e.getMessage());
        }
    }
} 