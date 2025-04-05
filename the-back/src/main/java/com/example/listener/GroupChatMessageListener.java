package com.example.listener;

import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;
import com.example.entity.vo.response.ChatMessage;
import com.example.service.ChatService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@Component
@RabbitListener(queues = "groupChat")
public class GroupChatMessageListener {

    @Resource
    ChatService chatService;

    @Resource
    RabbitTemplate rabbitTemplate;

    @RabbitHandler
    public void process(ChatMessage message){
        System.out.println("收到群聊消息：" + message);
        if(chatService.savePublicMessage(message) == false){
            //消息保存到mysql失败，重新发送消息到队列
            rabbitTemplate.convertAndSend("groupChat", message);
        }
    }
}
