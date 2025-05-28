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
import org.springframework.beans.factory.annotation.Autowired;
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
import com.example.service.VerificationCodeService;
import com.example.service.ChatCacheService;
import com.example.utils.RedisKeys;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;
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
    FriendsMapper friendsMapper;

    @Resource
    Group_memberMapper groupMemberMapper;

    @Autowired
    private VerificationCodeService verificationCodeService;

    @Autowired
    @Lazy
    private ChatCacheService chatCacheService;

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
            // 将验证码存入 Redis
            verificationCodeService.saveEmailCode(email, String.valueOf(code), java.time.Duration.ofMinutes(3));
            return null;
        }
    }

    //这个方法的作用是限制用户在60秒内只能发送1次验证码
    private boolean verifyLimit(String address){
        String key = RedisKeys.RATE_LIMIT_ONCE + address;
        return flowUtils.limitOnceCheck(key,60);
    }

    @Override
    public String registerEmailAccount(EmailRegisterVO vo) {
        String email = vo.getEmail();
        // 从 Redis 中获取验证码
        System.out.println(vo.getCode());
        String code = verificationCodeService.getEmailCode(email);
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
            // 删除 Redis 中的验证码
            verificationCodeService.deleteEmailCode(email);
            return null;
        }else{
            verificationCodeService.deleteEmailCode(email);
            return "内部错误，注册失败";
        }
    }
    
    @Override
    public String updatePasswordDueToForget(String email,String newPassword,String code){
        // 从 Redis 中获取验证码
        String codeInRedis = verificationCodeService.getEmailCode(email);
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
        // 删除验证码
        verificationCodeService.deleteEmailCode(email);
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

    @Override
    public Account getAccountById(int id){
        // 可以添加缓存注解如 @Cacheable("accounts")
        // 但用户信息更新频率低，直接查询数据库也可以接受
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

    @Override
    @Transactional // 添加事务注解确保数据一致性
    public String updateUsername(Integer userId, String newUsername) {
        if (newUsername == null || newUsername.trim().isEmpty()) {
            return "用户名不能为空";
        }
        
        newUsername = newUsername.trim();
        
        // 检查用户名长度
        if (newUsername.length() < 2 || newUsername.length() > 20) {
            return "用户名长度应在2-20个字符之间";
        }

        try {
            // 1. 获取旧用户名用于日志记录
            Account oldAccount = this.getAccountById(userId);
            if (oldAccount == null) {
                return "用户不存在";
            }
            String oldUsername = oldAccount.getUsername();
            
            // 2. 更新主账户表中的用户名
            boolean updated = this.update()
                    .eq("id", userId)
                    .set("username", newUsername)
                    .update();
            
            if (!updated) {
                return "用户名更新失败";
            }

            // 3. 全面清除相关缓存
            clearUserRelatedCacheExtended(userId, oldUsername, newUsername);

            // 4. 记录用户名修改日志
            System.out.println("用户名修改成功: 用户ID=" + userId + 
                             ", 旧用户名=" + oldUsername + 
                             ", 新用户名=" + newUsername);

            return null; // 成功返回null
        } catch (Exception e) {
            System.err.println("更新用户名失败: " + e.getMessage());
            return "更新用户名失败: " + e.getMessage();
        }
    }

    /**
     * 扩展的缓存清除方法 - 解决用户名修改后的数据一致性问题
     * @param userId 用户ID
     * @param oldUsername 旧用户名
     * @param newUsername 新用户名
     */
    private void clearUserRelatedCacheExtended(Integer userId, String oldUsername, String newUsername) {
        try {
            // 1. 清除聊天相关缓存
            chatCacheService.clearUserChatCache(userId);
            
            // 2. 清除可能包含用户名的群组缓存
            // 注意：群组消息中的senderName是动态查询的，所以清除群组缓存也很重要
            clearGroupRelatedCache(userId);
            
            // 3. 清除用户搜索相关缓存（如果有的话）
            clearUserSearchCache(oldUsername, newUsername);
            
            // 4. 发布用户名修改事件（用于通知其他服务或组件）
            publishUsernameChangeEvent(userId, oldUsername, newUsername);
            
            System.out.println("已全面清除用户 " + userId + " 的相关缓存");
        } catch (Exception e) {
            // 缓存清除失败不影响主要业务，只记录日志
            System.err.println("清除用户缓存失败: " + e.getMessage());
        }
    }
    
    /**
     * 清除群组相关缓存
     */
    private void clearGroupRelatedCache(Integer userId) {
        try {
            // 清除用户参与的所有群组的缓存
            // 这里可以查询用户参与的群组，然后清除相应的群组缓存
            // 由于群组消息中的senderName是动态查询的，清除群组缓存可以确保下次查询时获取最新用户名
            
            // 如果有群组缓存服务，可以这样调用：
            // groupCacheService.clearUserGroupsCache(userId);
            
            System.out.println("已清除用户 " + userId + " 的群组相关缓存");
        } catch (Exception e) {
            System.err.println("清除群组缓存失败: " + e.getMessage());
        }
    }
    
    /**
     * 清除用户搜索相关缓存
     */
    private void clearUserSearchCache(String oldUsername, String newUsername) {
        try {
            // 如果有用户搜索缓存，需要清除包含旧用户名的搜索结果
            // searchCacheService.clearUsernameCache(oldUsername);
            System.out.println("已清除用户名搜索缓存: " + oldUsername + " -> " + newUsername);
        } catch (Exception e) {
            System.err.println("清除搜索缓存失败: " + e.getMessage());
        }
    }
    
    /**
     * 发布用户名修改事件
     */
    private void publishUsernameChangeEvent(Integer userId, String oldUsername, String newUsername) {
        try {
            // 可以使用Spring Events或者消息队列发布事件
            // 通知其他组件用户名已修改，需要更新相关数据
            
            // 示例：使用RabbitMQ发布事件
            Map<String, Object> eventData = Map.of(
                "eventType", "USERNAME_CHANGED",
                "userId", userId,
                "oldUsername", oldUsername,
                "newUsername", newUsername,
                "timestamp", System.currentTimeMillis()
            );
            
            // amqpTemplate.convertAndSend("user.events", eventData);
            
            System.out.println("已发布用户名修改事件: " + eventData);
        } catch (Exception e) {
            System.err.println("发布用户名修改事件失败: " + e.getMessage());
        }
    }

    @Override
    public String updatePasswordInSettings(String email, String verificationCode, String newPassword) {
        // 验证验证码
        String codeInRedis = verificationCodeService.getEmailCode(email);
        if (codeInRedis == null) {
            return "验证码已过期，请重新获取";
        }
        if (!codeInRedis.equals(verificationCode)) {
            return "验证码错误";
        }

        // 验证邮箱是否存在
        if (!this.existsAccountByEmail(email)) {
            return "用户不存在";
        }

        // 验证新密码
        if (newPassword == null || newPassword.length() < 6) {
            return "密码长度不能少于6位";
        }

        try {
            // 加密新密码
            String encodedPassword = passwordEncoder.encode(newPassword);
            
            // 更新密码
            boolean updated = this.update()
                    .eq("email", email)
                    .set("password", encodedPassword)
                    .update();

            if (updated) {
                // 删除验证码
                verificationCodeService.deleteEmailCode(email);
                return null; // 成功返回null
            } else {
                return "密码更新失败";
            }
        } catch (Exception e) {
            return "更新密码失败: " + e.getMessage();
        }
    }

    /**
     * 清除用户相关的缓存
     * @param userId 用户ID
     */
    private void clearUserRelatedCache(Integer userId) {
        // 清除Redis中相关的缓存
        try {
            // 清除聊天相关缓存
            chatCacheService.clearUserChatCache(userId);
            
            // 可以调用其他缓存清除方法
            // friendCacheService.clearUserFriendsCache(userId);
            // groupCacheService.clearUserGroupsCache(userId);
            
            System.out.println("已清除用户 " + userId + " 的相关缓存");
        } catch (Exception e) {
            // 缓存清除失败不影响主要业务，只记录日志
            System.err.println("清除用户缓存失败: " + e.getMessage());
        }
    }
}
