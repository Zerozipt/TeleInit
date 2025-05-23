package com.example.listener;

import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

import com.example.entity.vo.response.ChatMessage;
import com.example.entity.vo.response.MessageAck;
import com.example.service.ChatService;

@Component
@RabbitListener(queues = "privateChat")
public class PrivateChatMessageListener {

    @Resource
    private ChatService chatService;

    @Resource
    private RabbitTemplate rabbitTemplate;
    
    @Resource
    private SimpMessagingTemplate messagingTemplate;

    @RabbitHandler
    public void receiveMessage(ChatMessage message) {
        // 将消息保存到数据库
        System.out.println("收到私聊消息：" + message);
        
        try {
            // 调用服务层方法保存消息，获取返回的消息ID
            String savedMessageId = chatService.savePrivateMessageWithId(message);
            
            if (savedMessageId != null) {
                System.out.println("私聊消息成功保存到数据库: " + message.getContent() + ", ID: " + savedMessageId);
                
                // 发送成功确认给发送者
                if (message.getTempId() != null) {
                    MessageAck ack = MessageAck.success(message.getTempId(), savedMessageId, "private");
                    messagingTemplate.convertAndSendToUser(
                        message.getSenderId().toString(),
                        "/queue/message-ack",
                        ack
                    );
                    System.out.println("已发送私聊消息确认: " + ack);
                }
            } else {
                System.err.println("私聊消息保存失败");
                
                // 发送失败确认给发送者
                if (message.getTempId() != null) {
                    MessageAck ack = MessageAck.failure(message.getTempId(), "数据库保存失败", "private");
                    messagingTemplate.convertAndSendToUser(
                        message.getSenderId().toString(),
                        "/queue/message-ack",
                        ack
                    );
                    System.out.println("已发送私聊消息失败确认: " + ack);
                }
            }
        } catch (Exception e) {
            System.err.println("处理私聊消息时发生错误: " + e.getMessage());
            e.printStackTrace();
            
            // 发送错误确认给发送者
            if (message.getTempId() != null) {
                MessageAck ack = MessageAck.failure(message.getTempId(), "处理消息时发生错误: " + e.getMessage(), "private");
                messagingTemplate.convertAndSendToUser(
                    message.getSenderId().toString(),
                    "/queue/message-ack",
                    ack
                );
                System.out.println("已发送私聊消息错误确认: " + ack);
            }
        }
    }
}