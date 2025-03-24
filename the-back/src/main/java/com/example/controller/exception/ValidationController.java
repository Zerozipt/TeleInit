package com.example.controller.exception;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.validation.ValidationException;
import com.example.entity.RestBean;
import lombok.extern.slf4j.Slf4j;

@Slf4j//日志记录
@RestControllerAdvice//全局异常处理
public class ValidationController {
    
    // 处理ValidationException异常
    // 当请求参数不符合@Validated注解的约束时，会抛出ValidationException异常
    // 例如：@Email 限制请求参数必须是邮箱格式
    // 例如：@Pattern 限制请求参数必须符合正则表达式
    @ExceptionHandler(ValidationException.class)
    public RestBean<String> handleValidationException(ValidationException e){
        log.warn("Resolve[{} : {}]", e.getClass().getName(),e.getMessage());
        return RestBean.failure(400, "请求参数错误");
    }
}
