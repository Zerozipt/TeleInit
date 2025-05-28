package com.example.controller;

import com.example.entity.RestBean;
import com.example.entity.dto.Account;
import com.example.entity.vo.request.UpdateUsernameRequest;
import com.example.entity.vo.request.UpdatePasswordRequest;
import com.example.entity.vo.response.UserProfileVO;
import com.example.service.AccountService;
import com.example.utils.JwtUtils;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.function.Supplier;

@Validated
@RestController
@RequestMapping("/api/user/settings")
public class UserSettingsController {

    @Resource
    private AccountService accountService;

    @Resource
    private JwtUtils jwtUtils;

    /**
     * 处理业务逻辑的通用方法
     */
    private RestBean<Void> messageHandler(Supplier<String> action) {
        String message = action.get();
        return message == null ? RestBean.success() : RestBean.failure(400, message);
    }

    /**
     * 从Authorization头中解析JWT获取用户ID
     */
    private Integer getUserIdFromToken(String authorization) {
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                System.err.println("Authorization header 格式错误: " + authorization);
                return null;
            }
            String jwt = authorization.substring(7);
            DecodedJWT decodedJWT = jwtUtils.resolveJWTFromLocalStorage(jwt);
            if (decodedJWT == null) {
                System.err.println("JWT解析失败，token可能已过期或无效");
                return null;
            }
            
            // 尝试多种方式获取用户ID
            String userIdStr = decodedJWT.getClaim("id").asString();
            if (userIdStr == null) {
                // 如果字符串形式为空，尝试整数形式
                Integer userIdInt = decodedJWT.getClaim("id").asInt();
                if (userIdInt != null) {
                    return userIdInt;
                }
                System.err.println("无法从JWT中获取用户ID");
                return null;
            }
            return Integer.parseInt(userIdStr);
        } catch (Exception e) {
            System.err.println("JWT解析异常: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取用户个人资料
     */
    @GetMapping("/profile")
    public RestBean<UserProfileVO> getUserProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            Integer userId = getUserIdFromToken(authorization);
            if (userId == null) {
                return RestBean.failure(401, "未提供有效的认证信息");
            }

            Account account = accountService.getAccountById(userId);
            if (account == null) {
                return RestBean.failure(404, "用户不存在");
            }

            UserProfileVO profile = new UserProfileVO();
            profile.setId(account.getId());
            profile.setUsername(account.getUsername());
            profile.setEmail(account.getEmail());
            profile.setRole(account.getRole());
            profile.setRegisterTime(account.getRegister_time());
            // profile.setAvatar(account.getAvatar()); // 暂时注释，avatar字段可能不存在

            return RestBean.success(profile);
        } catch (Exception e) {
            return RestBean.failure(500, "获取用户资料失败: " + e.getMessage());
        }
    }

    /**
     * 更新用户名
     */
    @PutMapping("/username")
    public RestBean<Void> updateUsername(
            @RequestBody UpdateUsernameRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            Integer userId = getUserIdFromToken(authorization);
            if (userId == null) {
                return RestBean.failure(401, "未提供有效的认证信息");
            }

            return messageHandler(() -> accountService.updateUsername(userId, request.getNewUsername()));
        } catch (Exception e) {
            return RestBean.failure(500, "更新用户名失败: " + e.getMessage());
        }
    }

    /**
     * 发送用于密码修改的验证码
     */
    @GetMapping("/password/send-code")
    public RestBean<Void> sendPasswordChangeCode(
            @RequestParam @Email String email,
            HttpServletRequest request) {
        return messageHandler(() -> 
                accountService.registerEmailVerifyCode("password-change", email, request.getRemoteAddr()));
    }

    /**
     * 更新密码
     */
    @PutMapping("/password")
    public RestBean<Void> updatePassword(@RequestBody UpdatePasswordRequest request) {
        return messageHandler(() -> 
                accountService.updatePasswordInSettings(
                        request.getEmail(),
                        request.getVerificationCode(),
                        request.getNewPassword()));
    }
} 