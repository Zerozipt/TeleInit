package com.example.controller;

import com.example.entity.dto.Group;
import com.example.entity.dto.Group_member;
import com.example.entity.vo.response.GroupDetailResponse;
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
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.util.Map;
import com.example.utils.JwtUtils;
import com.example.entity.RestBean;
import java.util.List;
import com.example.utils.GroupPermissionHelper;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private static final Logger log = LoggerFactory.getLogger(GroupController.class); // 添加日志记录器

    @Resource
    JwtUtils jwtUtils;

    @Resource
    GroupService groupService;

    @Resource // 2. 注入 AccountService (使用接口)
    AccountService accountService; // 假设 AccountService 有 findAccountIdByUsername 方法

    @Resource
    GroupPermissionHelper groupPermissionHelper;

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
    @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization)
     {
        try {
            String jwt = null;
            if (authorization != null && authorization.startsWith("Bearer ")) {
                jwt = authorization.substring(7);
            }
            
            if (jwt == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "未提供JWT令牌,请重新登陆"));
            }
            //解析jwt
            DecodedJWT decodedJWT = jwtUtils.resolveJWTFromLocalStorage(jwt);
            String username = decodedJWT.getClaim("name").asString();
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
                                       @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization)
                                       {
        try {
            String jwt = null;
            if (authorization != null && authorization.startsWith("Bearer ")) {
                jwt = authorization.substring(7);
            }
            
            if (jwt == null) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "未提供JWT令牌,请重新登陆"));
                }
            //解析jwt
            DecodedJWT decodedJWT = jwtUtils.resolveJWTFromLocalStorage(jwt);
            String username = decodedJWT.getClaim("name").asString();

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

    @PostMapping("/getGroupList")
    public RestBean<?> getGroupList(
        @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestBody Map<String, Object> requestBody
    )
    {
        try {
            String groupName = (String) requestBody.get("groupName");
            String jwt = null;
            if (authorization != null && authorization.startsWith("Bearer ")) {
                jwt = authorization.substring(7);
            }
            if (jwt == null) {
                return RestBean.unauthorized("未提供JWT令牌,请重新登陆");
            }
            //从mysql中获取groupName对应的group
            List<Group> group = groupService.getGroupByName(groupName);
            if (group == null) {
                return RestBean.failure(404, "群聊不存在");
            }   
            return RestBean.success(group);
        } catch (Exception e) {
            log.error("获取群聊列表失败: {}", e.getMessage(), e);
            return RestBean.failure(500, "获取群聊列表失败：");
        }
    }

    @PostMapping("/getGroupMembers")
    public RestBean<?> getGroupMembers(
        @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization
    )
    {
        try {
            String jwt = null;
            if (authorization != null && authorization.startsWith("Bearer ")) {
                jwt = authorization.substring(7);
            }
            if (jwt == null) {
                return RestBean.unauthorized("未提供JWT令牌,请重新登陆");
            }
            //解析jwt
            DecodedJWT decodedJWT = jwtUtils.resolveJWTFromLocalStorage(jwt);
            String userId = decodedJWT.getClaim("id").asString();
            //从mysql中获取userid加入的群组
            int userIdInt = Integer.parseInt(userId);
            List<Group_member> group_member = groupService.getGroupMembers(userIdInt);
            return RestBean.success(group_member);
        } catch (Exception e) {
            log.error("获取群聊成员列表失败: {}", e.getMessage(), e);
            return RestBean.failure(500, "获取群聊成员列表失败：");
        }
    }

    /**
     * 获取群组详情，包括所有成员和角色
     */
    @GetMapping("/{groupId}/detail")
    public RestBean<?> getGroupDetail(
            @PathVariable String groupId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            log.debug("开始获取群组详情: groupId={}", groupId);
            
            String jwt = null;
            if (authorization != null && authorization.startsWith("Bearer ")) {
                jwt = authorization.substring(7);
            }
            
            if (jwt == null) {
                log.warn("获取群组详情失败: 未提供JWT令牌, groupId={}", groupId);
                return RestBean.unauthorized("未提供JWT令牌,请重新登陆");
            }
            
            // 解析JWT获取用户ID
            DecodedJWT decodedJWT = jwtUtils.resolveJWTFromLocalStorage(jwt);
            int userId = Integer.parseInt(decodedJWT.getClaim("id").asString());
            log.debug("用户ID: {}, 请求群组ID: {}", userId, groupId);
            
            // 🔧 HOTFIX: 增强群组成员验证的错误处理
            boolean isMember;
            try {
                isMember = groupService.isGroupMember(groupId, userId);
                log.debug("群组成员检查结果: userId={}, groupId={}, isMember={}", userId, groupId, isMember);
            } catch (Exception e) {
                log.error("检查群组成员关系时发生错误: userId={}, groupId={}", userId, groupId, e);
                return RestBean.failure(500, "检查群组成员关系失败: " + e.getMessage());
            }
            
            if (!isMember) {
                log.warn("非群组成员尝试访问群组详情: userId={}, groupId={}", userId, groupId);
                return RestBean.failure(403, "只有群组成员才能查看群组详情");
            }
            
            // 获取群组详情
            GroupDetailResponse groupDetail;
            try {
                groupDetail = groupService.getGroupDetail(groupId);
                log.debug("群组详情查询结果: groupId={}, detail={}", groupId, 
                         groupDetail != null ? "found" : "null");
            } catch (Exception e) {
                log.error("获取群组详情时发生错误: groupId={}", groupId, e);
                return RestBean.failure(500, "获取群组详情失败: " + e.getMessage());
            }
            
            if (groupDetail == null) {
                log.warn("群组不存在: groupId={}", groupId);
                return RestBean.failure(404, "群组不存在");
            }
            
            log.info("成功获取群组详情: groupId={}, memberCount={}", groupId, 
                    groupDetail.getMemberCount());
            return RestBean.success(groupDetail);
            
        } catch (Exception e) {
            log.error("获取群组详情发生未预期错误: groupId={}", groupId, e);
            return RestBean.failure(500, "获取群组详情失败: " + e.getMessage());
        }
    }

    /**
     * 退出群组
     * @param groupId 群组ID
     * @param authorization JWT令牌
     * @return 操作结果
     */
    @PostMapping("/{groupId}/exit")
    public RestBean<Boolean> exitGroup(
            @PathVariable String groupId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return RestBean.failure(401, "未提供JWT令牌");
            }
            String jwt = authorization.substring(7);
            DecodedJWT decodedJWT = jwtUtils.resolveJWTFromLocalStorage(jwt);
            int userId = Integer.parseInt(decodedJWT.getClaim("id").asString());
            if (!groupService.isGroupMember(groupId, userId)) {
                return RestBean.failure(403, "您不是该群组成员");
            }
            boolean result = groupService.leaveGroup(groupId, userId);
            if (result) {
                return RestBean.success(true);
            } else {
                return RestBean.failure(500, "退出群组失败");
            }
        } catch (Exception e) {
            return RestBean.failure(500, "退出群组异常: " + e.getMessage());
        }
    }

    // ========== 群主管理功能API ==========

    /**
     * 移除群组成员（踢出成员）
     * @param groupId 群组ID
     * @param memberId 要移除的成员ID
     * @param authorization JWT令牌
     * @return 操作结果
     */
    @DeleteMapping("/{groupId}/members/{memberId}")
    public RestBean<Boolean> removeMember(
            @PathVariable String groupId,
            @PathVariable int memberId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return RestBean.failure(401, "未提供JWT令牌");
            }
            
            String jwt = authorization.substring(7);
            DecodedJWT decodedJWT = jwtUtils.resolveJWTFromLocalStorage(jwt);
            int operatorId = Integer.parseInt(decodedJWT.getClaim("id").asString());
            
            log.info("群主尝试移除成员: operatorId={}, groupId={}, memberId={}", operatorId, groupId, memberId);
            
            // 验证操作者权限
            if (!groupPermissionHelper.canManageMembers(groupId, operatorId)) {
                log.warn("用户无权限移除群成员: operatorId={}, groupId={}", operatorId, groupId);
                return RestBean.failure(403, "只有群主可以移除群成员");
            }
            
            // 验证操作目标有效性
            if (!groupPermissionHelper.isValidTarget(operatorId, memberId)) {
                log.warn("无效的移除操作: operatorId={}, memberId={}", operatorId, memberId);
                return RestBean.failure(400, "不能移除自己或目标用户无效");
            }
            
            // 执行移除操作
            boolean result = groupService.removeMember(groupId, memberId);
            if (result) {
                log.info("群成员移除成功: operatorId={}, groupId={}, memberId={}", operatorId, groupId, memberId);
                return RestBean.success(true);
            } else {
                log.warn("群成员移除失败: operatorId={}, groupId={}, memberId={}", operatorId, groupId, memberId);
                return RestBean.failure(500, "移除群成员失败");
            }
        } catch (Exception e) {
            log.error("移除群成员异常: groupId={}, memberId={}", groupId, memberId, e);
            return RestBean.failure(500, "移除群成员异常: " + e.getMessage());
        }
    }

    /**
     * 更新群组名称
     * @param groupId 群组ID
     * @param request 包含新名称的请求体
     * @param authorization JWT令牌
     * @return 操作结果
     */
    @PutMapping("/{groupId}/name")
    public RestBean<Boolean> updateGroupName(
            @PathVariable String groupId,
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return RestBean.failure(401, "未提供JWT令牌");
            }
            
            String jwt = authorization.substring(7);
            DecodedJWT decodedJWT = jwtUtils.resolveJWTFromLocalStorage(jwt);
            int operatorId = Integer.parseInt(decodedJWT.getClaim("id").asString());
            
            String newName = request.get("name");
            if (newName == null || newName.trim().isEmpty()) {
                return RestBean.failure(400, "群组名称不能为空");
            }
            
            log.info("群主尝试修改群名: operatorId={}, groupId={}, newName={}", operatorId, groupId, newName);
            
            // 验证操作者权限
            if (!groupPermissionHelper.canModifyGroupInfo(groupId, operatorId)) {
                log.warn("用户无权限修改群组信息: operatorId={}, groupId={}", operatorId, groupId);
                return RestBean.failure(403, "只有群主可以修改群组名称");
            }
            
            // 执行更新操作
            boolean result = groupService.updateGroupName(groupId, newName.trim());
            if (result) {
                log.info("群组名称更新成功: operatorId={}, groupId={}, newName={}", operatorId, groupId, newName);
                return RestBean.success(true);
            } else {
                log.warn("群组名称更新失败: operatorId={}, groupId={}, newName={}", operatorId, groupId, newName);
                return RestBean.failure(500, "群组名称更新失败，可能该名称已被使用");
            }
        } catch (Exception e) {
            log.error("更新群组名称异常: groupId={}, request={}", groupId, request, e);
            return RestBean.failure(500, "更新群组名称异常: " + e.getMessage());
        }
    }

    /**
     * 解散群组
     * @param groupId 群组ID
     * @param request 包含确认信息的请求体
     * @param authorization JWT令牌
     * @return 操作结果
     */
    @DeleteMapping("/{groupId}/dissolve")
    public RestBean<Boolean> dissolveGroup(
            @PathVariable String groupId,
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return RestBean.failure(401, "未提供JWT令牌");
            }
            
            String jwt = authorization.substring(7);
            DecodedJWT decodedJWT = jwtUtils.resolveJWTFromLocalStorage(jwt);
            int operatorId = Integer.parseInt(decodedJWT.getClaim("id").asString());
            
            String confirmText = request.get("confirmText");
            if (confirmText == null || confirmText.trim().isEmpty()) {
                return RestBean.failure(400, "请提供确认文本");
            }
            
            log.info("群主尝试解散群组: operatorId={}, groupId={}, confirmText={}", operatorId, groupId, confirmText);
            
            // 验证操作者权限
            if (!groupPermissionHelper.canModifyGroupInfo(groupId, operatorId)) {
                log.warn("用户无权限解散群组: operatorId={}, groupId={}", operatorId, groupId);
                return RestBean.failure(403, "只有群主可以解散群组");
            }
            
            // 获取群组信息验证确认文本
            GroupDetailResponse groupDetail = groupService.getGroupDetail(groupId);
            if (groupDetail == null) {
                return RestBean.failure(404, "群组不存在");
            }
            
            if (!confirmText.trim().equals(groupDetail.getName())) {
                log.warn("解散群组确认文本错误: operatorId={}, groupId={}, expected={}, actual={}", 
                        operatorId, groupId, groupDetail.getName(), confirmText);
                return RestBean.failure(400, "确认文本不正确，请输入群组名称");
            }
            
            // 执行解散操作
            boolean result = groupService.dissolveGroup(groupId);
            if (result) {
                log.info("群组解散成功: operatorId={}, groupId={}, groupName={}", 
                        operatorId, groupId, groupDetail.getName());
                return RestBean.success(true);
            } else {
                log.warn("群组解散失败: operatorId={}, groupId={}", operatorId, groupId);
                return RestBean.failure(500, "群组解散失败");
            }
        } catch (Exception e) {
            log.error("解散群组异常: groupId={}, request={}", groupId, request, e);
            return RestBean.failure(500, "解散群组异常: " + e.getMessage());
        }
    }
}