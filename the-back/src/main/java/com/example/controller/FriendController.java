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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.util.List;
import java.util.Map;
import com.alibaba.fastjson2.JSONObject;
import com.example.utils.JwtUtils;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.utils.ConvertUtils;
import com.example.entity.vo.response.FriendsResponse;
@RestController
@RequestMapping("/api/friends")
public class FriendController {

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private AccountService accountService;

    @Resource
    private ChatService chatService;

    @Resource
    private  SimpMessagingTemplate messagingTemplate;


    @PostMapping("/getFriends")
    public RestBean<List<FriendsResponse>> getFriends(
        @RequestBody Map<String, Object> requestBody) {
        try {
            String userIdStr = (String) requestBody.get("userId");
            if (userIdStr == null) {
                return RestBean.failure(400, "用户ID不能为空");
            }
            List<FriendsResponse> friends = chatService.getFriends(userIdStr);
            return RestBean.success(friends);
        } catch (NumberFormatException e) {
            return RestBean.failure(400, "无效的用户ID格式");
        } catch (ClassCastException e) {
            return RestBean.failure(400, "请求参数格式错误");
        } catch (Exception e) {
            return RestBean.failure(500, "获取好友列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/getFriendRequests")
    public RestBean<List<FriendsResponse>> getFriendRequests(
        @RequestBody Map<String, Object> requestBody) {
        try {
            String userIdStr = (String) requestBody.get("userId");
             if (userIdStr == null) {
                return RestBean.failure(400, "用户ID不能为空");
            }
            List<FriendsResponse> friendRequests = chatService.getFriendRequests(userIdStr);
            return RestBean.success(friendRequests);
        } catch (NumberFormatException e) {
            return RestBean.failure(400, "无效的用户ID格式");
        } catch (ClassCastException e) {
            return RestBean.failure(400, "请求参数格式错误");
        } catch (Exception e) {
            return RestBean.failure(500, "获取好友请求列表失败: " + e.getMessage());
        }
    }

    
    /**
     * 发送好友请求
     * @param requestBody 包含目标用户ID的请求体
     * @param authorization JWT令牌
     * @return 操作结果
     */
    @PostMapping("/request")
    public RestBean<Map<String, Object>> sendFriendRequest(
            @RequestBody Map<String, Object> requestBody,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization) {

        try {
            String jwt = null;
            if (authorization != null && authorization.startsWith("Bearer ")) {
                jwt = authorization.substring(7);
            }
            
            if (jwt == null) {
                return RestBean.failure(401, "未提供JWT令牌");
            }
            DecodedJWT decodedJWT = jwtUtils.resolveJWTFromLocalStorage(jwt);
            String currentUserIdStr = decodedJWT.getClaim("id").asString();
            int currentUserId = Integer.parseInt(currentUserIdStr);

            Object targetUserIdObj = requestBody.get("targetUserId");
            if (targetUserIdObj == null) {
                return RestBean.failure(400, "目标用户ID不能为空");
            }
            int targetUserId = Integer.parseInt(String.valueOf(targetUserIdObj));

            if (targetUserId <= 0) {
                return RestBean.failure(400, "无效的目标用户ID");
            }

            if (currentUserId == targetUserId) {
                return RestBean.failure(400, "不能添加自己为好友");
            }

            if (chatService.isFriend(currentUserId, targetUserId)) {
                return RestBean.failure(409, "已经是好友关系");
            }

            if (chatService.hasFriendRequest(currentUserId, targetUserId)) {
                return RestBean.failure(409, "已经发送过好友请求");
            }

            Friends friendRequest = new Friends();
            friendRequest.setTheFirstUserId(currentUserId);
            friendRequest.setTheSecondUserId(targetUserId);
            friendRequest.setStatus(Friends.Status.requested);
            friendRequest.setCreatedAt(new java.sql.Timestamp(System.currentTimeMillis()));

            chatService.saveFriendRequest(friendRequest);
            FriendsResponse friendsResponse = ConvertUtils.convertToFriendsResponse(friendRequest, accountService);
            
            messagingTemplate.convertAndSendToUser(
                String.valueOf(targetUserId),
                "/queue/system",
                new JSONObject()
                    .fluentPut("type", "friendRequest")
                    .fluentPut("content", "您有一条好友请求")
                    .fluentPut("friendsResponse", friendsResponse)
            );

            return RestBean.success(Map.of(
                "message", "好友请求已发送",
                "requestId", friendRequest.getId()
            ));
        } catch (NumberFormatException e) {
            return RestBean.failure(400, "用户ID格式无效: " + e.getMessage());
        } catch (ClassCastException e) {
            return RestBean.failure(400, "请求参数格式错误: " + e.getMessage());
        } catch (Exception e) {
            return RestBean.failure(500, "发送好友请求失败: " + e.getMessage());
        }
    }

    /**
     * 接受好友请求
     * @param requestBody 包含发送者ID和接收者ID的请求体
     * @param authorization JWT令牌 (用于校验操作权限，但当前版本根据用户指示忽略)
     * @return 操作结果
     */
    @PostMapping("/requests/received") // Renamed method, kept endpoint for compatibility
    public RestBean<Boolean> acceptFriendRequest(
            @RequestBody Map<String, Object> requestBody,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            Object senderIdObj = requestBody.get("senderId");
            Object receiverIdObj = requestBody.get("receiverId");

            if (senderIdObj == null || receiverIdObj == null) {
                return RestBean.failure(400, "发送者或接收者ID不能为空");
            }

            int senderId = Integer.parseInt(String.valueOf(senderIdObj));
            int receiverId = Integer.parseInt(String.valueOf(receiverIdObj));

            // TODO: 验证 current user (from JWT) == receiverId，根据用户指示暂时忽略

            if (senderId <= 0 || receiverId <= 0) {
                return RestBean.failure(400, "无效的用户ID");
            }

            boolean result = chatService.ReceivedFriendRequests(senderId, receiverId); // Service method name is still ReceivedFriendRequests

            if(result){
                FriendsResponse friendsResponse = new FriendsResponse();
                friendsResponse.setFirstUserId(String.valueOf(senderId));
                friendsResponse.setSecondUserId(String.valueOf(receiverId));
                Account sender = accountService.getAccountById(senderId);
                Account receiver = accountService.getAccountById(receiverId);
                if (sender != null) friendsResponse.setFirstUsername(sender.getUsername());
                if (receiver != null) friendsResponse.setSecondUsername(receiver.getUsername());
                friendsResponse.setStatus(FriendsResponse.Status.accepted);
                
                messagingTemplate.convertAndSendToUser(
                    String.valueOf(senderId),
                    "/queue/system",
                    new JSONObject()
                        .fluentPut("type", "friendAccept")
                        .fluentPut("content", "您的好友请求已被接受")
                        .fluentPut("friendsResponse", friendsResponse)
                );
                 return RestBean.success(true);
            } else {
                 return RestBean.failure(500, "接受好友请求失败，可能请求不存在或状态不正确");
            }
        } catch (NumberFormatException e) {
            return RestBean.failure(400, "用户ID格式无效: " + e.getMessage());
        } catch (ClassCastException e) {
            return RestBean.failure(400, "请求参数格式错误: " + e.getMessage());
        } catch (Exception e) {
            return RestBean.failure(500, "接受好友请求操作失败: " + e.getMessage());
        }
    }


    /**
     * 拒绝好友请求
     * @param requestBody 包含发送者ID和接收者ID的请求体
     * @param authorization JWT令牌
     * @return 操作结果
     */
    @PostMapping("/requests/reject") // Renamed method
    public RestBean<Boolean> handleRejectFriendRequest(
            @RequestBody Map<String, Object> requestBody,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            String jwt = null;
            if (authorization != null && authorization.startsWith("Bearer ")) {
                jwt = authorization.substring(7);
            }
            if (jwt == null) {
                return RestBean.failure(401, "未提供JWT令牌");
            }
            DecodedJWT decodedJWT = jwtUtils.resolveJWTFromLocalStorage(jwt);
            String currentUserIdStr = decodedJWT.getClaim("id").asString();
            int currentUserId = Integer.parseInt(currentUserIdStr); // This is the receiver

            Object senderIdOfRequestObj = requestBody.get("senderId"); // ID of user who sent the request
            Object receiverIdFromPayloadObj = requestBody.get("receiverId"); // Should match currentUserId

            if (senderIdOfRequestObj == null || receiverIdFromPayloadObj == null) {
                return RestBean.failure(400, "发送者或接收者ID不能为空");
            }

            int senderIdOfRequest = Integer.parseInt(String.valueOf(senderIdOfRequestObj));
            int receiverIdFromPayload = Integer.parseInt(String.valueOf(receiverIdFromPayloadObj));

            // 验证操作权限：当前JWT用户必须是请求体中的receiverId
            if (currentUserId != receiverIdFromPayload) {
                return RestBean.failure(403, "无权操作他人好友请求");
            }

            if (senderIdOfRequest <= 0 || currentUserId <= 0) {
                return RestBean.failure(400, "无效的用户ID");
            }
            
            boolean result = chatService.rejectFriendRequestByUsers(currentUserId, senderIdOfRequest);
            if (result) {
                // 通知被拒绝的用户
                Account rejectedUserAccount = accountService.getAccountById(senderIdOfRequest); // 获取被拒绝用户的信息以获取用户名
                Account currentUserAccount = accountService.getAccountById(currentUserId); // 获取当前用户信息（拒绝者）

                JSONObject payload = new JSONObject();
                payload.put("type", "friendRequestRejected");
                payload.put("message", (currentUserAccount != null ? currentUserAccount.getUsername() : "用户 " + currentUserId) + " 拒绝了您的好友请求");
                payload.put("senderId", senderIdOfRequest); // 原请求的发送者 (被拒绝者)
                payload.put("receiverId", currentUserId);   // 原请求的接收者 (操作拒绝者)
                if (rejectedUserAccount != null) {
                    payload.put("rejectedByUsername", currentUserAccount != null ? currentUserAccount.getUsername() : "未知用户");
                }
                if (currentUserAccount != null) {
                    payload.put("rejectedUserUsername", rejectedUserAccount != null ? rejectedUserAccount.getUsername() : "未知用户");
                }

                messagingTemplate.convertAndSendToUser(
                    String.valueOf(senderIdOfRequest), // 发送给被拒绝的用户
                    "/queue/system",
                    payload
                );
                 return RestBean.success(true);
            } else {
                return RestBean.failure(500, "拒绝好友请求失败，可能请求不存在或已被处理");
            }
        } catch (NumberFormatException e) {
            return RestBean.failure(400, "用户ID格式无效: " + e.getMessage());
        } catch (ClassCastException e) {
            return RestBean.failure(400, "请求参数格式错误: " + e.getMessage());
        } catch (Exception e) {
            return RestBean.failure(500, "拒绝好友请求操作失败: " + e.getMessage());
        }
    }

    /**
     * 取消已发送的好友请求
     * @param requestBody 包含目标用户ID (接收请求的用户ID)
     * @param authorization JWT令牌
     * @return 操作结果
     */
    @PostMapping("/requests/cancel")
    public RestBean<Boolean> cancelFriendRequest(
            @RequestBody Map<String, Object> requestBody,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            String jwt = null;
            if (authorization != null && authorization.startsWith("Bearer ")) {
                jwt = authorization.substring(7);
            }
            if (jwt == null) {
                return RestBean.failure(401, "未提供JWT令牌");
            }
            DecodedJWT decodedJWT = jwtUtils.resolveJWTFromLocalStorage(jwt);
            String currentUserIdStr = decodedJWT.getClaim("id").asString(); // User canceling request (sender of original request)
            int currentUserId = Integer.parseInt(currentUserIdStr);

            Object targetUserIdObj = requestBody.get("targetUserId"); // User who received the original request
            if (targetUserIdObj == null) {
                return RestBean.failure(400, "目标用户ID不能为空");
            }
            int targetUserId = Integer.parseInt(String.valueOf(targetUserIdObj));

            if (currentUserId <= 0 || targetUserId <= 0) {
                return RestBean.failure(400, "无效的用户ID");
            }

            boolean result = chatService.cancelFriendRequest(currentUserId, targetUserId);
            if (result) {
                // 通知原请求的接收者，请求已被取消
                Account cancellerAccount = accountService.getAccountById(currentUserId); // 发起取消操作的用户
                // Account targetAccount = accountService.getAccountById(targetUserId); // 原本接收请求的用户

                JSONObject payload = new JSONObject();
                payload.put("type", "friendRequestCancelledBySender");
                payload.put("message", (cancellerAccount != null ? cancellerAccount.getUsername() : "用户 " + currentUserId) + " 取消了发给您的好友请求。");
                payload.put("cancellerId", currentUserId); // 发起取消操作的用户ID
                payload.put("originalReceiverId", targetUserId); // 原本接收请求的用户ID (即通知的目标)
                if (cancellerAccount != null) {
                    payload.put("cancellerUsername", cancellerAccount.getUsername());
                }

                messagingTemplate.convertAndSendToUser(
                    String.valueOf(targetUserId), // 发送给原请求的接收者
                    "/queue/system",
                    payload
                );
                return RestBean.success(true);
            } else {
                return RestBean.failure(500, "取消好友请求失败，可能请求不存在或已被处理");
            }
        } catch (NumberFormatException e) {
            return RestBean.failure(400, "用户ID格式无效: " + e.getMessage());
        } catch (ClassCastException e) {
            return RestBean.failure(400, "请求参数格式错误: " + e.getMessage());
        } catch (Exception e) {
            return RestBean.failure(500, "取消好友请求操作失败: " + e.getMessage());
        }
    }

    /**
     * 删除好友
     * @param requestBody 包含目标用户ID
     * @param authorization JWT令牌
     * @return 操作结果
     */
    @PostMapping("/delete")
    public RestBean<Boolean> deleteFriend(
            @RequestBody Map<String, Object> requestBody,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return RestBean.failure(401, "未提供JWT令牌");
            }
            String jwt = authorization.substring(7);
            DecodedJWT decodedJWT = jwtUtils.resolveJWTFromLocalStorage(jwt);
            int currentUserId = Integer.parseInt(decodedJWT.getClaim("id").asString());
            Object targetUserIdObj = requestBody.get("targetUserId");
            if (targetUserIdObj == null) {
                return RestBean.failure(400, "目标用户ID不能为空");
            }
            int targetUserId = Integer.parseInt(String.valueOf(targetUserIdObj));
            boolean result = chatService.removeFriend(currentUserId, targetUserId);
            if (result) {
                return RestBean.success(true);
            } else {
                return RestBean.failure(500, "删除好友失败");
            }
        } catch (Exception e) {
            return RestBean.failure(500, "删除好友异常: " + e.getMessage());
        }
    }
} 