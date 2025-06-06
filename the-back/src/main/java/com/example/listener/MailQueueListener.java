package com.example.listener;


import jakarta.annotation.Resource;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component 
@RabbitListener(queues = "email")
public class MailQueueListener {

    @Resource
    JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    String username;

    //发送邮件
    @RabbitHandler
    //经过这个函数后，消息从队列中删除，如果想要保留，需要设置autoDelete为false，也可以重新发送消息到队列中
    public void sendMailMessage(Map<String, Object> data){
        String email = (String)data.get("email").toString();
        Integer code = (Integer) data.get("code");
        String type = (String) data.get("type");
        SimpleMailMessage message = switch(type) {
            case "register" -> createMessage("注册验证码", "您的验证码是：" + code +",有效时间3分钟，请勿向他人泄露验证码信息", email);
            case "reset" -> createMessage("找回密码验证码", "您的验证码是：" + code
                    + ",有效时间三分钟，若非本人操作，请无视该邮件", email);
            case "password-change" -> createMessage("密码修改验证码", "您的验证码是：" + code
                    + ",有效时间三分钟，用于密码修改验证，若非本人操作，请无视该邮件", email);
            default -> null;
        };
        if(message == null) return;
        mailSender.send(message);
        //邮件发送成功后，自动将该消息从队列中删除
    }

    //创建邮件
    private SimpleMailMessage createMessage(String title,String content,String email){
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(username);
        message.setTo(email);
        message.setSubject(title);
        message.setText(content);
        return message;
    }

}
