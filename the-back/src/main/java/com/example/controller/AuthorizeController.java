package com.example.controller;

import com.example.entity.RestBean;
import com.example.service.AccountService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.entity.vo.request.EmailRegisterVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.function.Supplier;
@Validated  
@RestController
@RequestMapping("/api/auth")
public class AuthorizeController {

    @Resource
    AccountService accountService;
    
    //处理消息的函数,如果消息为空，则返回成功，否则返回失败
    //Supplier<String> action 是一个函数式接口，表示一个没有参数且返回String的方法
    //action.get() 表示执行action方法，并返回结果
    private RestBean<Void> messageHandler(Supplier<String> action){
        String message = action.get();
        return message == null ? RestBean.success() : RestBean.failure(400, message);
    }

    //login接口不需要自己编写，因为spring security已经为我们提供了
    //我们只需要在配置类中配置即可
    //配置类在SecurityConfig类中

    @GetMapping("/ask-code")
    //Pattern 正则表达式 作用是限制请求参数的格式
    public RestBean<Void> askVerifyCode(@RequestParam @Pattern(regexp = "register|reset") String type, 
                                        @RequestParam @Email String email,
                                        HttpServletRequest request) {
        return messageHandler(() -> 
                accountService.registerEmailVerifyCode(type, email, request.getRemoteAddr()));
    }

    @PostMapping("/register")
    public RestBean<Void> register(@RequestBody EmailRegisterVO vo){
        return messageHandler(() -> accountService.registerEmailAccount(vo));
    }

    @PostMapping("/reset-password")
    public RestBean<Void> resetPassword(@RequestParam @Email String email,
                                        @RequestParam String code,
                                        @RequestParam String password){
        return messageHandler(() -> accountService.updatePasswordDueToForget(email, password, code));
    }

}
