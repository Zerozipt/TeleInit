package com.example.listener;

import com.example.service.ChatService;
import com.example.utils.Const;
import com.example.entity.vo.response.FriendsResponse;
import com.example.entity.vo.response.StatusMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import com.example.service.OnlineStatusService;

import jakarta.annotation.Resource;
import java.security.Principal;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket事件监听器，用于处理连接断开等事件
 */
@Component
public class WebSocketEventListener implements ApplicationListener<SessionDisconnectEvent> {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    @Resource
    private ChatService chatService;

    @Autowired
    private OnlineStatusService onlineStatusService;

    @Override
    public void onApplicationEvent(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal userPrincipal = headerAccessor.getUser();

        if (userPrincipal != null && userPrincipal.getName() != null) {
            String userId = userPrincipal.getName();
            System.out.println("WebSocket连接意外断开，用户: " + userId);

            // 标记用户离线
            onlineStatusService.markOffline(userId);
            System.out.println("用户 " + userId + " 因连接断开，在线状态已删除");

            // 2. 通知其好友该用户已下线
            try {
                StatusMessage statusMessage = new StatusMessage();
                statusMessage.setUserId(userId);
                statusMessage.setStatus("OFFLINE");

                List<FriendsResponse> friends = chatService.getFriends(userId);
                for (FriendsResponse friend : friends) {
                    String friendTargetId = null;
                    // 找到非当前掉线用户的那个ID作为通知目标
                    if (userId.equals(friend.getFirstUserId())) {
                        friendTargetId = friend.getSecondUserId();
                    } else if (userId.equals(friend.getSecondUserId())) {
                        friendTargetId = friend.getFirstUserId();
                    }

                    if (friendTargetId != null) {
                        messagingTemplate.convertAndSendToUser(
                            friendTargetId,
                            "/queue/offline",
                            statusMessage
                        );
                        System.out.println("已通知好友 " + friendTargetId + " 关于用户 " + userId + " 的离线状态");
                    }
                }
            } catch (Exception e) {
                System.err.println("处理用户 " + userId + " 意外断开并通知好友时发生错误: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
} 