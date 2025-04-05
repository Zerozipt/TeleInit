package com.example.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration 
public class RabbitConfiguration {

    // 配置消息转换器,原因是springboot默认使用jdk的序列化方式,而rabbitmq默认使用json序列化方式
    @Bean
    public MessageConverter jsonMessageConverter() {        
        return new Jackson2JsonMessageConverter();
    }
    
    // 配置rabbitTemplate,设置消息转换器
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }

    // 配置email队列
    @Bean("emailQueue")
    public Queue emailQueue(){
        return QueueBuilder
                .durable("email") // durable: 是否持久化，参数是队列名称
                .build();
    }

    //配置一个队列用于存放私聊消息
    @Bean("privateChatQueue")
    public Queue privateChatQueue(){
        return QueueBuilder
                .durable("privateChat")
                .build();
    }

    //配置一个队列用于存放群聊消息
    @Bean("groupChatQueue")
    public Queue groupChatQueue(){
        return QueueBuilder
                .durable("groupChat")
                .build();
    }

    //配置一个队列用户存放好友请求
    @Bean("friendRequestQueue")
    public Queue friendRequestQueue(){
        return QueueBuilder
                .durable("friendRequest")
                .build();
    }
}
