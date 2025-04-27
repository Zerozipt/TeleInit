package com.example.listener;

import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

import com.example.entity.vo.response.ChatMessage;
import com.example.service.ChatService;

@Component
@RabbitListener(queues = "privateChat")
public class PrivateChatMessageListener {

    @Resource
    private ChatService chatService;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @RabbitHandler
    public void receiveMessage(ChatMessage message) {
        // 将消息保存到数据库
        System.out.println("收到私聊消息：" + message);
        
        try {
            // 调用服务层方法保存消息
            boolean saved = chatService.savePrivateMessage(message);
            if (saved) {
                System.out.println("私聊消息成功保存到数据库: " + message.getContent());
            } else {
                System.err.println("私聊消息保存失败");
            }
        } catch (Exception e) {
            System.err.println("处理私聊消息时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}