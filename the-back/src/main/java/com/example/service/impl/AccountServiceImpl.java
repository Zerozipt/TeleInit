package com.example.service.impl;
import java.util.List;
import java.util.stream.Collectors;
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
import org.springframework.dao.DataAccessException;
import com.alibaba.fastjson2.JSON;
import com.example.mapper.FriendsMapper;
import com.example.entity.dto.Friends;
import com.example.mapper.Group_memberMapper;
import com.example.entity.dto.Group_member;
@Service //extends ServiceImpl<AccountMapper, Account> implements AccountService
public class AccountServiceImpl extends ServiceImpl<AccountMapper, Account> implements AccountService {

    @Resource
    //密码加密器
    PasswordEncoder passwordEncoder;

    @Resource
    //流量限制器
    FlowUtils flowUtils;

    @Resource
    AmqpTemplate amqpTemplate;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    FriendsMapper friendsMapper;

    @Resource
    Group_memberMapper groupMemberMapper;

    //修改security的loadUserByUsername方法
    //我们这个业务只允许邮箱登录，因为我们不限制用户名唯一
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = this.getAccountByEmail(username);
        if (account == null) {
            throw new UsernameNotFoundException("邮箱或密码错误");
        }
        return User
                .withUsername(account.getUsername())
                .password(account.getPassword())
                .roles(account.getRole())
                .build();
    }


    //根据用户名或邮箱获取用户
    public Account getAccountByUsernameOrEmail(String text) {
        return this.query()
                .eq("username", text)
                .or()
                .eq("email", text)
                .one();
    }

    public Account getAccountByEmail(String email){
        return this.query()
                .eq("email", email)
                .one();
    }


    //根据邮箱获取用户密码
    public String getPasswordByEmail(String email){
        return this.query()
                .eq("email", email)
                .select("password")
                .one()
                .getPassword();
    }
    

    //根据邮箱，传入新密码，更新密码
    public String updatePasswordByEmail(String email,String newPassword){
        try{
            this.update()
                .eq("email", email)
                .set("password", newPassword)
                .update();
            return "success";
        }catch(DataAccessException e){
            //返回具体错误信息
            return e.getMessage();
        }
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
        //由于我们不限制用户名唯一，所以不需要判断用户名是否已被注册
        // if(this.existsAccountByUsername(vo.getUsername())){
        //     return "用户名已被注册";
        // }
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
            stringRedisTemplate.delete(Const.VERIFY_EMAIL_Data + email);
            return "内部错误，注册失败";
        }
    }
    
    @Override
    public String updatePasswordDueToForget(String email,String newPassword,String code){
        //从redis中获取验证码
        String codeInRedis = stringRedisTemplate.opsForValue().get(Const.VERIFY_EMAIL_Data + email);
        if(codeInRedis == null){
            return "请先获取验证码";
        }
        if(!code.equals(codeInRedis)){
            return "验证码错误";
        }
        //验证邮箱是否存在
        if(!this.existsAccountByEmail(email)){
            return "邮箱不存在";
        }
        //将新密码进行加密
        String encodedPassword = passwordEncoder.encode(newPassword);
        //更新密码
        String result = this.updatePasswordByEmail(email, encodedPassword);
        if(result.equals("success")){
            //删除redis中的验证码
            stringRedisTemplate.delete(Const.VERIFY_EMAIL_Data + email);
            return null;
        }else{
            stringRedisTemplate.delete(Const.VERIFY_EMAIL_Data + email);
            return result;
        }

    }

    @Override
    public Account getAccountById(int id){
        return this.query()
                .eq("id", id)
                .one();
    }
    
    private boolean existsAccountByEmail(String email){
        return this.baseMapper.exists(Wrappers.<Account>query().eq("email",email));
    }

    private boolean existsAccountByUsername(String username){
        return this.baseMapper.exists(Wrappers.<Account>query().eq("username",username));
    }

    /**
     * 根据用户名查找账户信息
     * @param username 用户名
     * @return 账户信息，如果找不到则返回 null
     */
    @Override // 如果接口里声明了，这里加上 @Override
    public Account findAccountByUsername(String username) {
        // 使用 MyBatis-Plus 的查询构造器根据 username 查找
        return this.query().eq("username", username).one();
    }

    /**
     * 根据用户名获取用户ID
     * @param username 用户名
     * @return 用户ID (String 类型)，如果找不到则返回 null
     */
    @Override // 如果接口里声明了，这里加上 @Override
    public int findIdByUsername(String username) {
        Account account = this.query()
                .eq("username", username)
                .one();
        return account != null ? account.getId() : 0;
    }

    @Override
    public List<Account> searchUsers(String searchTerm) {
        // 如果搜索词为空，返回空列表
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return List.of();
        }
        
        // 使用模糊查询搜索用户名包含该词的用户
        return this.query()
                .like("username", searchTerm)
                .or()
                .like("email", searchTerm)
                .orderByDesc("id")
                .list();
    }
}
