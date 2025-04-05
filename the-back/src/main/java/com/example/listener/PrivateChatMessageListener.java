package com.example.listener;

import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import com.example.entity.vo.response.PrivateChatMessage;
import com.example.service.ChatService;

@Component
@RabbitListener(queues = "privateChat")
public class PrivateChatMessageListener {

    @Resource
    private ChatService chatService;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @RabbitHandler
    public void receiveMessage(PrivateChatMessage message) {
        // 将消息保存到数据库 调用server中的方法
       System.out.println("收到私聊消息：" + message);

    }
}