package com.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.entity.dto.Account;
import com.example.entity.vo.request.EmailRegisterVO;
import org.springframework.security.core.userdetails.UserDetailsService;
import java.util.List;

public interface AccountService extends IService<Account>, UserDetailsService {
    Account getAccountByUsernameOrEmail(String text);
    
    /**
     * 通过邮箱注册账户
     * @param vo 注册信息
     * @return 错误信息，无错误时返回null
     */
    String registerEmailAccount(EmailRegisterVO vo);
    
    /**
     * 生成邮箱验证码
     * @param type 验证码类型
     * @param email 邮箱
     * @param ip 请求的ip地址
     * @return 错误信息，无错误时返回null
     */
    String registerEmailVerifyCode(String type, String email, String ip);

    /**
     * 更新密码
     * @param email 邮箱
     * @param password 新密码
     * @param code 验证码
     * @return 错误信息，无错误时返回null
     */
    String updatePasswordDueToForget(String email, String password, String code);
    
    /**
     * 通过用户名查找对应的账户信息
     * @param username 用户名
     * @return 账户信息，如果找不到则返回 null
     */
    Account findAccountByUsername(String username);

    /**
     * 通过用户名查找对应的用户ID
     * @param username 用户名
     * @return 用户ID (String 类型)，如果找不到则返回 null
     */
    int findIdByUsername(String username);
    
    /**
     * 通过用户ID查找对应的账户信息
     * @param id 用户ID
     * @return 账户信息
     */
    Account getAccountById(int id);

    /**
     * 搜索用户
     * @param searchTerm 搜索关键词（用户名或邮箱）
     * @return 用户列表
     */
    List<Account> searchUsers(String searchTerm);
}
