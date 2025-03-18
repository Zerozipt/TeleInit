package com.example.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.entity.dto.Account;
import com.example.mapper.AccountMapper;
import com.example.service.AccountService;
import com.example.utils.Const;
import com.example.utils.FlowUtils;
import jakarta.annotation.Resource;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import com.example.entity.vo.request.EmailRegisterVO;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.entity.dto.Account;
import java.util.Date;
@Service
public class AccountServiceImpl extends ServiceImpl<AccountMapper, Account> implements AccountService {

    @Resource
    PasswordEncoder passwordEncoder;
    @Resource
    FlowUtils flowUtils;

    @Resource
    AmqpTemplate amqpTemplate;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = this.getAccountByUsernameOrEmail(username);
        if (account == null) {
            throw new UsernameNotFoundException("用户名或密码错误");
        }
        return User
                .withUsername(username)
                .password(account.getPassword())
                .roles(account.getRole())
                .build();
    }

    public Account getAccountByUsernameOrEmail(String text) {
        return this.query()
                .eq("username", text)
                .or()
                .eq("email", text)
                .one();
    }

    /*
     * 发送邮箱验证码
     * @param type 验证码类型
     * @param email 邮箱
     * @param ip 请求的ip地址
     * @return 验证码
     */
    @Override
    public String registerEmailVerifyCode(String type, String email,String ip) {
        //synchronized的作用是防止多线程同时访问同一资源
        //这里使用了String的intern()方法来获取字符串的唯一引用
        //这样可以确保在多线程环境中，只有一个线程能够访问这个代码块
        //这样可以避免多个线程同时访问同一资源
        //这里使用了ip来作为锁对象
        synchronized (ip.intern()) {
            if (!this.verifyLimit(ip)) {
                return "请求频繁，请稍后再试";
            }
            Random random = new Random();
            //生成一个六位数验证码
            int code = random.nextInt(899999) + 100000;
            Map<String, Object> data = Map.of("type", type, "email", email, "code", code);
            amqpTemplate.convertAndSend("email", data);
            //将验证码存入redis
            stringRedisTemplate.opsForValue()
                    .set(Const.VERIFY_EMAIL_Data + email, String.valueOf(code),
                            3, TimeUnit.MINUTES);
            return null;
        }
    }

    //这个方法的作用是限制用户在60秒内只能发送1次验证码
    private boolean verifyLimit(String address){
        String key = Const.VERIFY_EMAIL_LIMIT + address;
        return flowUtils.limitOnceCheck(key,60);
    }

    @Override
    public String registerEmailAccount(EmailRegisterVO vo) {
        String email = vo.getEmail();
        //从redis中获取验证码
        System.out.println(vo.getCode());
        String code = stringRedisTemplate.opsForValue().get(Const.VERIFY_EMAIL_Data + email);
        if(code == null){
            return "请先获取验证码";
        }
        if(!code.equals(vo.getCode())){
            return "验证码错误";
        }
        //判断邮箱是否已被注册
        if(this.existsAccountByEmail(email)){
            return "邮箱已被注册";
        }
        //判断用户名是否已被注册
        if(this.existsAccountByUsername(vo.getUsername())){
            return "用户名已被注册";
        }
        String password = vo.getPassword();
        String encodedPassword = passwordEncoder.encode(password);
        Account account = new Account();
        account.setEmail(email);
        account.setUsername(vo.getUsername());
        account.setPassword(encodedPassword);
        account.setRole("user");
        account.setRegister_time(new Date());
        if(this.save(account)){
            //删除redis中的验证码
            stringRedisTemplate.delete(Const.VERIFY_EMAIL_Data + email);
            return null;
        }else{
            return "内部错误，注册失败";
        }
    }
    
    private boolean existsAccountByEmail(String email){
        return this.baseMapper.exists(Wrappers.<Account>query().eq("email",email));
    }

    private boolean existsAccountByUsername(String username){
        return this.baseMapper.exists(Wrappers.<Account>query().eq("username",username));
    }
}
