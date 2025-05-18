package com.example.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.entity.RestBean;
import com.example.entity.vo.request.GroupInvitationRequest;
import com.example.entity.vo.response.GroupInvitationResponse;
import com.example.service.GroupInvitationService;
import com.example.service.GroupService;
import com.example.utils.JwtUtils;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups/invitations")
public class GroupInvitationController {
    
    private static final Logger logger = LoggerFactory.getLogger(GroupInvitationController.class);
    
    @Resource
    private GroupInvitationService invitationService;
    
    @Resource
    private GroupService groupService;
    
    @Resource
    private JwtUtils jwtUtils;
    
    /**
     * 发送群组邀请
     */
    @PostMapping
    public RestBean<?> inviteUserToGroup(
            @RequestBody GroupInvitationRequest request,
            @RequestHeader("Authorization") String authorization) {
        try {
            String jwt = null;
            if (authorization != null && authorization.startsWith("Bearer ")) {
                jwt = authorization.substring(7);
            }
            
            if (jwt == null) {
                return RestBean.unauthorized("未提供JWT令牌");
            }
            
            // 解析JWT获取用户ID
            DecodedJWT decodedJWT = jwtUtils.resolveJWTFromLocalStorage(jwt);
            int inviterId = Integer.parseInt(decodedJWT.getClaim("id").asString());
            
            // 参数验证
            if (request.getGroupId() == null || request.getInviteeId() == null) {
                return RestBean.failure(400, "群组ID和被邀请者ID不能为空");
            }
            
            // 验证用户是否为群组成员
            if (!groupService.isGroupMember(request.getGroupId(), inviterId)) {
                return RestBean.failure(403, "只有群组成员才能邀请他人加入");
            }
            
            // 发送邀请
            boolean result = invitationService.inviteUserToGroup(
                    request.getGroupId(), inviterId, request.getInviteeId());
            
            if (result) {
                return RestBean.success("邀请发送成功");
            } else {
                return RestBean.failure(400, "邀请发送失败，可能是重复邀请或其他原因");
            }
            
        } catch (Exception e) {
            logger.error("发送群组邀请失败", e);
            return RestBean.failure(500, "发送邀请时发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 接受群组邀请
     */
    @PostMapping("/{invitationId}/accept")
    public RestBean<?> acceptInvitation(
            @PathVariable long invitationId,
            @RequestHeader("Authorization") String authorization) {
        try {
            String jwt = null;
            if (authorization != null && authorization.startsWith("Bearer ")) {
                jwt = authorization.substring(7);
            }
            
            if (jwt == null) {
                return RestBean.unauthorized("未提供JWT令牌");
            }
            
            // 解析JWT获取用户ID
            DecodedJWT decodedJWT = jwtUtils.resolveJWTFromLocalStorage(jwt);
            int userId = Integer.parseInt(decodedJWT.getClaim("id").asString());
            
            // 接受邀请
            boolean result = invitationService.respondToInvitation(invitationId, userId, "accepted");
            
            if (result) {
                return RestBean.success("已接受邀请");
            } else {
                return RestBean.failure(400, "接受邀请失败");
            }
            
        } catch (Exception e) {
            logger.error("接受群组邀请失败", e);
            return RestBean.failure(500, "接受邀请时发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 拒绝群组邀请
     */
    @PostMapping("/{invitationId}/reject")
    public RestBean<?> rejectInvitation(
            @PathVariable long invitationId,
            @RequestHeader("Authorization") String authorization) {
        try {
            String jwt = null;
            if (authorization != null && authorization.startsWith("Bearer ")) {
                jwt = authorization.substring(7);
            }
            
            if (jwt == null) {
                return RestBean.unauthorized("未提供JWT令牌");
            }
            
            // 解析JWT获取用户ID
            DecodedJWT decodedJWT = jwtUtils.resolveJWTFromLocalStorage(jwt);
            int userId = Integer.parseInt(decodedJWT.getClaim("id").asString());
            
            // 拒绝邀请
            boolean result = invitationService.respondToInvitation(invitationId, userId, "rejected");
            
            if (result) {
                return RestBean.success("已拒绝邀请");
            } else {
                return RestBean.failure(400, "拒绝邀请失败");
            }
            
        } catch (Exception e) {
            logger.error("拒绝群组邀请失败", e);
            return RestBean.failure(500, "拒绝邀请时发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 获取收到的邀请
     */
    @GetMapping("/received")
    public RestBean<?> getReceivedInvitations(
            @RequestHeader("Authorization") String authorization) {
        try {
            String jwt = null;
            if (authorization != null && authorization.startsWith("Bearer ")) {
                jwt = authorization.substring(7);
            }
            
            if (jwt == null) {
                return RestBean.unauthorized("未提供JWT令牌");
            }
            
            // 解析JWT获取用户ID
            DecodedJWT decodedJWT = jwtUtils.resolveJWTFromLocalStorage(jwt);
            int userId = Integer.parseInt(decodedJWT.getClaim("id").asString());
            
            // 获取收到的邀请
            List<GroupInvitationResponse> invitations = invitationService.getUserReceivedInvitations(userId);
            
            return RestBean.success(invitations);
            
        } catch (Exception e) {
            logger.error("获取群组邀请失败", e);
            return RestBean.failure(500, "获取邀请时发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 获取发出的邀请
     */
    @GetMapping("/sent")
    public RestBean<?> getSentInvitations(
            @RequestHeader("Authorization") String authorization) {
        try {
            String jwt = null;
            if (authorization != null && authorization.startsWith("Bearer ")) {
                jwt = authorization.substring(7);
            }
            
            if (jwt == null) {
                return RestBean.unauthorized("未提供JWT令牌");
            }
            
            // 解析JWT获取用户ID
            DecodedJWT decodedJWT = jwtUtils.resolveJWTFromLocalStorage(jwt);
            int userId = Integer.parseInt(decodedJWT.getClaim("id").asString());
            
            // 获取发出的邀请
            List<GroupInvitationResponse> invitations = invitationService.getUserSentInvitations(userId);
            
            return RestBean.success(invitations);
            
        } catch (Exception e) {
            logger.error("获取发出的群组邀请失败", e);
            return RestBean.failure(500, "获取邀请时发生错误: " + e.getMessage());
        }
    }
} 