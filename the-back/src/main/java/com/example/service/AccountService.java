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
    Account findAccountByUsername(String username);

    /**
     * 根据用户名获取用户ID
     * @param username 用户名
     * @return 用户ID (String 类型)，如果找不到则返回 null
     */
    int findIdByUsername(String username);

    /**
     * 搜索用户
     * @param searchTerm 搜索关键词
     * @return 用户列表
     */
    List<Account> searchUsers(String searchTerm);

    /**
     * 更新用户名
     * @param userId 用户ID
     * @param newUsername 新用户名
     * @return 操作结果信息
     */
    String updateUsername(Integer userId, String newUsername);

    /**
     * 更新密码（设置页面）
     * @param email 邮箱
     * @param verificationCode 验证码
     * @param newPassword 新密码
     * @return 操作结果信息
     */
    String updatePasswordInSettings(String email, String verificationCode, String newPassword);

    /**
     * 根据邮箱获取用户信息
     * @param email 邮箱
     * @return 用户信息
     */
    Account getAccountByEmail(String email);
}
