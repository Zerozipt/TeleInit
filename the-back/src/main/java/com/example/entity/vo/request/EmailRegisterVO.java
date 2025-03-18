package com.example.entity.vo.request;

import org.hibernate.validator.constraints.Length;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class EmailRegisterVO {
    @Email
    String email;

    @Length(min = 6, max = 6)
    String code;

    //这个正则表达式的意思是：用户名只能包含数字、字母、中文、下划线、中划线、点、@符号
    @Pattern(regexp = "^[a-zA-Z0-9\\u4e00-\\u9fa5]+$")
    @Length(min = 3, max = 16)
    String username;

    @Length(min = 6, max = 20)
    String password;

}
