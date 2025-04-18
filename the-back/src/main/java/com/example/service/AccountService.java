package com.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.entity.dto.Account;
import com.example.entity.vo.request.EmailRegisterVO;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import java.util.List;
public interface AccountService extends IService<Account>, UserDetailsService {
    Account getAccountByUsernameOrEmail(String text);

    String getPasswordByEmail(String email);

    String updatePasswordByEmail(String email,String newPassword);

    String registerEmailVerifyCode(String type, String email,String ip);

    String registerEmailAccount(EmailRegisterVO vo);

    String updatePasswordDueToForget(String email,String newPassword,String code);  

    Account getAccountById(int id);

    /**
     * 根据用户名查找账户信息
     * @param username 用户名
     * @return 账户信息，如果找不到则返回 null
     */
    Account findAccountByUsername(String username); // <-- 添加这个方法声明

    /**
     * 根据用户名获取用户ID
     * @param username 用户名
     * @return 用户ID (String 类型)，如果找不到则返回 null
     */
    int findIdByUsername(String username); // <-- 添加这个方法声明
}
