package com.example.websocket;

//要引入spring6的包
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.http.server.ServerHttpRequest;
import java.security.Principal;
import java.util.Map;
import jakarta.annotation.Resource;
import com.example.utils.JwtUtils;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import java.util.Collections;
import org.springframework.messaging.MessageChannel;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.entity.vo.request.CustomPrincipal;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Resource
    JwtUtils jwtUtils;

    @Override  
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {  
        registry.addEndpoint("/ws-chat")  
                .setAllowedOriginPatterns("*")  
                .setHandshakeHandler(new DefaultHandshakeHandler() {  
                    @Override  
                    protected CustomPrincipal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {  
                        // 从URL参数中获取令牌  
                        String token = null;  
                        String query = request.getURI().getQuery();  
                        if (query != null && query.contains("token=")) {  
                            // 简单解析出token参数  
                            int tokenIndex = query.indexOf("token=");  
                            if (tokenIndex >= 0) {  
                                token = query.substring(tokenIndex + 6);  
                                // 如果URL中有其他参数，则需要截断  
                                int endIndex = token.indexOf("&");  
                                if (endIndex >= 0) {  
                                    token = token.substring(0, endIndex);  
                                }  
                            }  
                            
                            System.out.println("从URL获取的token: " + token);  
                            if (token != null) {  
                                // 验证JWT令牌  
                                DecodedJWT jwt = jwtUtils.resolveJWTFromLocalStorage(token);  
                                if (jwt != null) {  
                                    String username = jwt.getClaim("name").asString();
                                    String userId = jwt.getClaim("id").asString();  // 假设JWT中包含用户ID的claim名为"id"
                                    System.out.println("JWT验证成功，用户名: " + username + ", 用户ID: " + userId);  
                                    
                                    return new CustomPrincipal(userId, username);  
                                }  
                            }  
                        }  
                        System.out.println("JWT验证失败或未找到令牌");  
                        return null;  
                    }  
                })  
                .withSockJS();  
    }  

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry registry) {
        // 客户端订阅地址的前缀
        registry.enableSimpleBroker("/topic", "/queue");
        // 客户端发送消息地址的前缀,意思是当客户端发送消息时，消息会被发送到/app路径
        registry.setApplicationDestinationPrefixes("/app");
        // 点对点消息前缀
        registry.setUserDestinationPrefix("/user");
    }
    
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    Principal user = accessor.getUser();
                    if (user != null) {
                        // 将用户名添加到认证成功后的会话属性中
                        System.out.println("用户名：" + user.getName());
                        accessor.setSessionAttributes(Collections.singletonMap("user-name", user.getName()));
                    }
                }
                return message;
            }
        });
    }
    
    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECTED.equals(accessor.getCommand())) {
                    // 获取认证的用户
                    Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
                    if (sessionAttributes != null && sessionAttributes.containsKey("user-name")) {
                        // 在CONNECTED帧中添加用户名
                        accessor.addNativeHeader("user-name", (String) sessionAttributes.get("user-name"));
                    }
                }
                return message;
            }
        });
    }

    @Bean
    public org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate(org.springframework.messaging.simp.SimpMessageSendingOperations brokerMessagingTemplate) {
        return (org.springframework.messaging.simp.SimpMessagingTemplate) brokerMessagingTemplate;
    }


}
// ... existing code ...