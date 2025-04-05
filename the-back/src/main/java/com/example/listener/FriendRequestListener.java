package com.example.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class FriendRequestListener {
    @RabbitListener(queues = "friendRequest")
    public void receiveFriendRequest(String message){
        System.out.println("收到好友请求：" + message);
    }
}
