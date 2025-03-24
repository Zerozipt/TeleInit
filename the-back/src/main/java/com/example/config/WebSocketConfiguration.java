package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration
@EnableWebSocket
//@EnableWebSocket的注解是用来启用WebSocket的，它会在Spring容器中注册一个WebSocketEndpointExporter，
//这个Exporter会扫描所有带有@ServerEndpoint注解的类，并将其注册为WebSocket端点。
public class WebSocketConfiguration{

    //ServerEndpointExporter是Spring提供的一个类，用于将带有@ServerEndpoint注解的类注册为WebSocket端点。
    @Bean
    public ServerEndpointExporter serverEndpointExporter(){
        return new ServerEndpointExporter();
    }

}
