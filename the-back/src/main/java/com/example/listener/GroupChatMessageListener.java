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
    
    // 增强重试机制
    private final Map<String, Integer> retryCountMap = new ConcurrentHashMap<>();
    private final int MAX_RETRY_COUNT = 3; // 最大重试次数
    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

    @RabbitHandler
    public void process(ChatMessage message){
        System.out.println("收到群聊消息：" + message);

        // 改进消息唯一标识符生成
        String messageId = generateMessageId(message);
        
        // 获取当前重试次数
        int currentRetryCount = retryCountMap.getOrDefault(messageId, 0);

        try {
            // 调用服务层方法保存消息，获取返回的消息ID
            String savedMessageId = chatService.savePublicMessageWithId(message);
            
            if (savedMessageId != null) {
                System.out.println("群聊消息成功保存到数据库: " + message.getContent() + ", ID: " + savedMessageId);
                
                // 清理重试记录
                retryCountMap.remove(messageId);
                
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
                handleSaveFailure(message, messageId, currentRetryCount, "数据库保存失败");
            }
        } catch (Exception e) {
            System.err.println("处理群聊消息时发生错误: " + e.getMessage());
            e.printStackTrace();
            handleSaveFailure(message, messageId, currentRetryCount, "处理消息时发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 生成更可靠的消息唯一标识符
     */
    private String generateMessageId(ChatMessage message) {
        return String.format("%s:%s:%s:%d", 
            message.getTimestamp(), 
            message.getSenderId(), 
            message.getGroupId(), 
            message.getContent().hashCode());
    }
    
    /**
     * 处理消息保存失败
     */
    private void handleSaveFailure(ChatMessage message, String messageId, int currentRetryCount, String errorMessage) {
        if (currentRetryCount < MAX_RETRY_COUNT) {
            // 增加重试次数并延迟重试
            retryCountMap.put(messageId, currentRetryCount + 1);
            
            // 延迟重试，避免立即重试造成资源压力
            scheduledExecutor.schedule(() -> {
                System.out.println("重试群聊消息保存，第 " + (currentRetryCount + 1) + " 次重试: " + messageId);
                rabbitTemplate.convertAndSend("groupChat", message);
            }, (currentRetryCount + 1) * 2, TimeUnit.SECONDS); // 递增延迟
            
            // 设置清理任务，避免内存泄漏
            scheduledExecutor.schedule(() -> {
                retryCountMap.remove(messageId);
            }, 10, TimeUnit.MINUTES);
            
        } else {
            // 超过最大重试次数，发送失败确认
            System.err.println("群聊消息保存失败，已达到最大重试次数: " + messageId);
            retryCountMap.remove(messageId);
            
            if (message.getTempId() != null) {
                MessageAck ack = MessageAck.failure(message.getTempId(), errorMessage, "group");
                messagingTemplate.convertAndSendToUser(
                    message.getSenderId().toString(),
                    "/queue/message-ack",
                    ack
                );
                System.out.println("已发送群聊消息失败确认: " + ack);
            }
        }
    }
}