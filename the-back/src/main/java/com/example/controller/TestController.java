package com.example.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestController {
    /**
     * 测试接口，返回"hello"。
     * @return "hello"
     */
    @GetMapping("/hello")
    public String test() {
        return "hello";
    }
}