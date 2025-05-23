package com.example.listener;

import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;
import com.example.entity.vo.response.ChatMessage;
import com.example.entity.vo.response.MessageAck;
import com.example.service.ChatService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@RabbitListener(queues = "groupChat")
public class GroupChatMessageListener {

    @Resource
    ChatService chatService;

    @Resource
    RabbitTemplate rabbitTemplate;
    
    @Resource
    private SimpMessagingTemplate messagingTemplate;
    
    private final Map<String, Boolean> retryMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

    @RabbitHandler
    public void process(ChatMessage message){
        System.out.println("收到群聊消息：" + message);

        // 为消息生成唯一标识符
        String messageId = message.getTimestamp() + ":" + message.getSenderId() + ":" +
                           message.getGroupId() + ":" + message.getContent().hashCode();

        try {
            // 调用服务层方法保存消息，获取返回的消息ID
            String savedMessageId = chatService.savePublicMessageWithId(message);
            
            if (savedMessageId != null) {
                System.out.println("群聊消息成功保存到数据库: " + message.getContent() + ", ID: " + savedMessageId);
                
                // 发送成功确认给发送者
                if (message.getTempId() != null) {
                    MessageAck ack = MessageAck.success(message.getTempId(), savedMessageId, "group");
                    messagingTemplate.convertAndSendToUser(
                        message.getSenderId().toString(),
                        "/queue/message-ack",
                        ack
                    );
                    System.out.println("已发送群聊消息确认: " + ack);
                }
            } else {
                System.err.println("群聊消息保存失败");
                
                // 检查这条特定消息是否已经重试过
                if(!retryMap.containsKey(messageId)){
                    rabbitTemplate.convertAndSend("groupChat", message);
                    retryMap.put(messageId, true);

                    // 添加过期清理，避免内存泄漏
                    scheduledExecutor.schedule(() -> {
                        retryMap.remove(messageId);
                    }, 5, TimeUnit.MINUTES);
                } else {
                    // 重试失败，发送失败确认
                    if (message.getTempId() != null) {
                        MessageAck ack = MessageAck.failure(message.getTempId(), "数据库保存失败", "group");
                        messagingTemplate.convertAndSendToUser(
                            message.getSenderId().toString(),
                            "/queue/message-ack",
                            ack
                        );
                        System.out.println("已发送群聊消息失败确认: " + ack);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("处理群聊消息时发生错误: " + e.getMessage());
            e.printStackTrace();
            
            // 发送错误确认给发送者
            if (message.getTempId() != null) {
                MessageAck ack = MessageAck.failure(message.getTempId(), "处理消息时发生错误: " + e.getMessage(), "group");
                messagingTemplate.convertAndSendToUser(
                    message.getSenderId().toString(),
                    "/queue/message-ack",
                    ack
                );
                System.out.println("已发送群聊消息错误确认: " + ack);
            }
        }
    }
}