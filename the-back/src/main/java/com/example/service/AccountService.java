package com.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.entity.dto.Account;
import com.example.entity.vo.request.EmailRegisterVO;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface AccountService extends IService<Account>, UserDetailsService {
    Account getAccountByUsernameOrEmail(String text);

    String registerEmailVerifyCode(String type, String email,String ip);

    String registerEmailAccount(EmailRegisterVO vo);
}
