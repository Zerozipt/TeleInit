package com.example.controller;

import com.example.entity.dto.Group;
import com.example.entity.dto.Group_member;
import com.example.service.AccountService; // 1. 导入 AccountService 接口
import com.example.service.GroupService;
// import com.example.service.impl.AccountServiceImpl; // 通常注入接口而非实现类
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.Getter;
import org.slf4j.Logger; // 建议添加日志
import org.slf4j.LoggerFactory; // 建议添加日志
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private static final Logger log = LoggerFactory.getLogger(GroupController.class); // 添加日志记录器

    @Resource
    GroupService groupService;

    @Resource // 2. 注入 AccountService (使用接口)
    AccountService accountService; // 假设 AccountService 有 findAccountIdByUsername 方法


    // --- 创建群聊的请求体 DTO ---
    @Getter
    @Data
    public static class CreateGroupRequest {
        private String name;
    }

    /**
     * 创建群聊
     * @param request 包含群名称的请求体
     * @param userDetails 由 Spring Security 注入的当前认证用户信息
     * @return ResponseEntity 包含创建结果或错误信息
     */
    @PostMapping
    public ResponseEntity<?> createGroup(@RequestBody CreateGroupRequest request,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "用户未认证"));
            }
            String username = userDetails.getUsername();

            // 3. 使用 AccountService 通过用户名查找 int 类型的用户 ID
            //    假设方法名为 findAccountIdByUsername，你需要根据实际情况修改
            int creatorId;
            try {
                // 假设 findAccountIdByUsername 返回 int，如果找不到可能抛异常或返回特殊值 (如 0 或 -1)
                creatorId = accountService.findIdByUsername(username); // <-- 修改这里调用你服务的方法
                if (creatorId <= 0) { // 根据你的 ID 规则调整判断条件
                    log.warn("无法根据用户名 '{}' 找到有效的用户ID", username);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "无法关联认证用户"));
                }
            } catch (Exception e) { // 更具体地捕获可能的异常，如 UserNotFoundException
                log.error("根据用户名 '{}' 查找用户ID时出错: {}", username, e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "查找用户ID时出错"));
            }


            if (request == null || request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "群聊名称不能为空"));
            }

            // 4. 调用 GroupService，传入 int 类型的 creatorId
            Group newGroup = groupService.createGroup(request.getName(), creatorId);
            return ResponseEntity.status(HttpStatus.CREATED).body(newGroup);

        } catch (IllegalArgumentException e) {
            // Service 层抛出的参数错误或名称重复错误
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("创建群聊失败: {}", e.getMessage(), e); // 记录内部错误
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "创建群聊时发生内部错误")); // 不要暴露过多细节
        }
    }

    /**
     * 加入群聊 (按群聊名称)
     * @param groupName 要加入的群聊名称 (从路径变量获取) <-- 5. 修改路径变量名
     * @param userDetails 由 Spring Security 注入的当前认证用户信息
     * @return ResponseEntity 包含加入结果或错误信息
     */
    // 5. 修改 API 路径以反映按名称加入
    @PostMapping("/{groupName}/members")
    public ResponseEntity<?> joinGroup(@PathVariable String groupName, // <-- 5. 修改 @PathVariable 对应名称
                                       @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "用户未认证"));
            }
            String username = userDetails.getUsername();

            // 6. 使用 AccountService 通过用户名查找 int 类型的用户 ID
            int userId;
            try {
                userId = accountService.findIdByUsername(username); // <-- 修改这里调用你服务的方法
                if (userId <= 0) {
                    log.warn("无法根据用户名 '{}' 找到有效的用户ID", username);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "无法关联认证用户"));
                }
            } catch (Exception e) {
                log.error("根据用户名 '{}' 查找用户ID时出错: {}", username, e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "查找用户ID时出错"));
            }

            // 7. 调用 GroupService，传入 groupName 和 int 类型的 userId
            Group_member joinedMember = groupService.joinGroup(groupName, userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(joinedMember);

        } catch (IllegalArgumentException e) {
            // Service 层抛出的参数错误
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            // 区分 Service 层已知错误和未知内部错误
            if (e.getMessage() != null && e.getMessage().contains("群聊不存在")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
            }
            if (e.getMessage() != null && e.getMessage().contains("用户已在该群聊中")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
            }
            // 其他 RuntimeException 视为内部错误
            log.error("加入群聊 {} 失败: {}", groupName, e.getMessage(), e); // 记录内部错误
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "加入群聊时发生内部错误")); // 不要暴露过多细节
        }
    }
}