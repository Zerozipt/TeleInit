package com.example.controller;

import com.example.entity.dto.Group;
import com.example.entity.dto.Group_member;
import com.example.entity.vo.request.CustomPrincipal;
import com.example.service.GroupService;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    @Resource
    GroupService groupService;

    // --- DTO for Create Group Request ---
    @Data
    public static class CreateGroupRequest {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    /**
     * 创建群聊
     * @param request 包含群名称的请求体
     * @param principal 由 Spring Security 注入的当前认证用户信息
     * @return ResponseEntity 包含创建结果或错误信息
     */
    @PostMapping
    public ResponseEntity<?> createGroup(@RequestBody CreateGroupRequest request,
                                         @AuthenticationPrincipal CustomPrincipal principal) { // <-- 注入 CustomPrincipal
        try {
            // 检查 principal 是否为空 (理论上，如果接口受保护，不应为空)
            if (principal == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "用户未认证"));
            }
            // 从 Principal 获取用户 ID
            String creatorId = principal.getUserId();

            if (request == null || request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "群聊名称不能为空"));
            }

            Group newGroup = groupService.createGroup(request.getName(), creatorId);
            return ResponseEntity.status(HttpStatus.CREATED).body(newGroup);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            // log.error("创建群聊失败: {}", e.getMessage(), e); // 建议添加日志
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "创建群聊时发生内部错误: " + e.getMessage()));
        }
    }

    /**
     * 加入群聊
     * @param groupId 要加入的群聊ID (从路径变量获取)
     * @param principal 由 Spring Security 注入的当前认证用户信息
     * @return ResponseEntity 包含加入结果或错误信息
     */
    @PostMapping("/{groupId}/members")
    public ResponseEntity<?> joinGroup(@PathVariable String groupId,
                                       @AuthenticationPrincipal CustomPrincipal principal) { // <-- 注入 CustomPrincipal
        try {
            // 检查 principal 是否为空
            if (principal == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "用户未认证"));
            }
            // 从 Principal 获取用户 ID
            String userId = principal.getUserId();

            Group_member joinedMember = groupService.joinGroup(groupId, userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(joinedMember);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            // log.error("加入群聊 {} 失败: {}", groupId, e.getMessage(), e); // 建议添加日志
            if (e.getMessage().contains("群聊不存在")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
            }
            if (e.getMessage().contains("用户已在该群聊中")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "加入群聊时发生内部错误: " + e.getMessage()));
        }
    }

    // --- 其他接口 (获取群信息、成员列表等) 保持不变 ---
}