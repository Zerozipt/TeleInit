package com.example.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import java.security.Principal;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class JwtUtils {
    @Value("${spring.security.jwt.key}")
    String key;

    @Value("${spring.security.jwt.expire}")
    int expire;

    @Resource
    StringRedisTemplate template;

    // ★★★ 添加 Logger 实例 ★★★
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    /**
     * 使JWT令牌失效。
     * @param headerToken 请求头中的令牌
     * @return 是否成功使令牌失效
     */
    public boolean invalidateJWT(String headerToken) {
        // 去除Bearer前缀并获取实际的JWT令牌
        String token = this.converToken(headerToken);
        if (token == null) return false;
        // 使用HMAC256算法创建JWT验证器
        Algorithm algorithm = Algorithm.HMAC256(key);
        JWTVerifier jwtVerifier = JWT.require(algorithm).build();
        try {
            // 验证JWT令牌
            DecodedJWT jwt = jwtVerifier.verify(token);
            // 获取JWT的ID
            String id = jwt.getId();
            // 删除令牌
            return deleteToken(id, jwt.getExpiresAt());
        } catch (JWTVerificationException e) {
            // 如果验证失败，返回false
            return false;
        }
    }

    /**
     * 删除令牌。
     * @param uuid 令牌ID
     * @param time 令牌过期时间
     * @return 是否成功删除令牌
     */
    private boolean deleteToken(String uuid, Date time) {
        // 检查令牌是否已经无效
        if (this.isInvalidToken(uuid)) return false;
        // 获取当前时间
        Date now = new Date();
        // 计算令牌剩余的有效时间
        long expire = Math.max(time.getTime() - now.getTime(), 0);
        template.opsForValue().set(Const.JWT_BLACK_LIST + uuid, "", expire, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * 检查令牌是否无效。
     * @param uuid 令牌ID
     * @return 令牌是否无效
     */
    private boolean isInvalidToken(String uuid) {
        // ★★★ 日志：方法入口和参数 ★★★
        logger.debug("进入 isInvalidToken 方法，检查 UUID: {}", uuid);

        if (uuid == null) {
            // ★★★ 日志：处理 null UUID 的情况 ★★★
            logger.warn("isInvalidToken 方法收到的 UUID 为 null，将视为无效 Token");
            return true; // 如果 UUID 为 null，通常应视为无效
        }

        // 构造要检查的 Redis Key
        String keyToCheck = Const.JWT_BLACK_LIST + uuid;
        // ★★★ 日志：将要检查的 Redis Key ★★★
        logger.debug("准备对 Redis 进行黑名单检查，Key: {}", keyToCheck);

        Boolean exists = null; // 用于存储 Redis 操作结果
        try {
            // ★★★ 执行 Redis 检查 ★★★
            exists = template.hasKey(keyToCheck);
            // ★★★ 日志：Redis 检查的实际结果 ★★★
            logger.debug("Redis 检查结果 template.hasKey('{}'): {}", keyToCheck, exists);

        } catch (Exception e) {
            // ★★★ 日志：捕获 Redis 操作可能发生的异常 ★★★
            logger.error("在检查 Redis Key '{}' 时发生异常", keyToCheck, e);
            // 在 Redis 异常时，为了安全起见，可以考虑将 Token 视为无效
            logger.warn("由于 Redis 检查异常，将 Token 视为无效处理");
            return true;
        }

        // 根据 Redis 返回结果判断 Token 是否无效
        // 注意：template.hasKey 可能返回 null (虽然较少见)，所以用 Boolean.TRUE.equals 更安全
        boolean isInvalid = Boolean.TRUE.equals(exists);

        // ★★★ 日志：方法最终的返回值 ★★★
        logger.debug("isInvalidToken 方法返回: {}", isInvalid);
        return isInvalid;
    }

    /**
     * 解析JWT令牌。
     * @param headerToken 请求头中的令牌
     * @return 解析后的JWT
     */
    public DecodedJWT resolveJWT(String headerToken) {
        // 去除Bearer前缀并获取实际的JWT令牌
        String token = this.converToken(headerToken);
        if (token == null) return null;
        // 使用HMAC256算法创建JWT验证器
        Algorithm algorithm = Algorithm.HMAC256(key);
        JWTVerifier jwtVerifier = JWT.require(algorithm).build();
        try {
            // 验证JWT令牌
            DecodedJWT verify = jwtVerifier.verify(token);
            // 检查令牌是否已经无效
            if (this.isInvalidToken(verify.getId())) return null;
            // 获取令牌的过期时间
            Date expire = verify.getExpiresAt();
            // 检查令牌是否已过期
            return new Date().before(expire) ? verify : null;
        } catch (JWTVerificationException e) {
            // 如果验证失败，返回null
            return null;
        }
    }

    //和resolveJWT一样，但是传输过来的token是直接从localstorage中获取的，不需要去除Bearer前缀
    public DecodedJWT resolveJWTFromLocalStorage(String token) {
        // 使用HMAC256算法创建JWT验证器
        Algorithm algorithm = Algorithm.HMAC256(key);
        JWTVerifier jwtVerifier = JWT.require(algorithm).build();
        try {
            // 验证JWT令牌
            DecodedJWT verify = jwtVerifier.verify(token);
            // 检查令牌是否已经无效
            if (this.isInvalidToken(verify.getId())) return null;
            // 获取令牌的过期时间
            Date expire = verify.getExpiresAt();
            // 检查令牌是否已过期
            return new Date().before(expire) ? verify : null;
        } catch (JWTVerificationException e) {
            // 如果验证失败，返回null
            return null;
        }
    }

    /**
     * 创建JWT令牌。
     * @param details 用户详情
     * @param id 用户ID
     * @param username 用户名
     * @param userId 用户唯一ID
     * @return JWT令牌
     */
    public String CreateJWT(UserDetails details, int id, String username) {
        // 使用HMAC256算法创建JWT生成器
        Algorithm algorithm = Algorithm.HMAC256(key);
        // 获取令牌的过期时间
        Date expireTime = this.expireTime();
        String userId = String.valueOf(id);
        // 创建JWT令牌
        return JWT.create()
                .withJWTId(UUID.randomUUID().toString())
                .withClaim("id", userId)
                .withClaim("name", username)
                .withClaim("userId", userId)
                .withClaim("authorities", details.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).toList())
                .withExpiresAt(expireTime)
                .withIssuedAt(new Date())
                .sign(algorithm);
    }

    /**
     * 获取令牌过期时间。
     * @return 令牌过期时间
     */
    public Date expireTime() {
        // 获取当前时间
        Calendar calendar = Calendar.getInstance();
        // 设置过期时间为当前时间加上配置的过期天数
        calendar.add(Calendar.HOUR, expire * 24);
        return calendar.getTime();
    }

    /**
     * 转换请求头中的令牌。
     * @param headerToken 请求头中的令牌
     * @return 转换后的令牌
     */
    public String converToken(String headerToken) {
        // 检查请求头中的令牌是否为空或不以Bearer开头
        if (headerToken == null || !headerToken.startsWith("Bearer ")) {
            return null;
        }
        // 去除Bearer前缀
        return headerToken.substring(7);
    }

    /**
     * 将JWT转换为UserDetails对象。
     * @param jwt 解析后的JWT
     * @return UserDetails对象
     */
    public UserDetails toUser(DecodedJWT jwt) {
        // 获取JWT的所有声明
        Map<String, Claim> claims = jwt.getClaims();
        // 创建UserDetails对象
        return User
                .withUsername(claims.get("name").asString())
                .password("******")
                .authorities(claims.get("authorities").asArray(String.class))
                .build();
    }

    /**
     * 从JWT中提取用户ID。
     * @param jwt 解析后的JWT
     * @return 用户ID
     */
    public Integer tpId(DecodedJWT jwt) {
        // 获取JWT的所有声明
        Map<String, Claim> claims = jwt.getClaims();
        // 提取用户ID
        return claims.get("id").asInt();
    }

    //返回Principal对象
    public Principal parseToken(String token) {
        // 去除Bearer前缀
        String jwt = token.substring(7);
        // 使用HMAC256算法创建JWT验证器
        Algorithm algorithm = Algorithm.HMAC256(key);
        JWTVerifier jwtVerifier = JWT.require(algorithm).build();
        // 验证JWT令牌
        DecodedJWT verify = jwtVerifier.verify(jwt);
        // 获取JWT的所有声明
        Map<String, Claim> claims = verify.getClaims();
        // 创建Principal对象
        return new Principal() {
            @Override
            public String getName() {
                return claims.get("name").asString();
            }
        };  
    }

    public String getUsernameFromToken(String token) {
        // 去除Bearer前缀
        String jwt = token.substring(7);
        // 使用HMAC256算法创建JWT验证器
        Algorithm algorithm = Algorithm.HMAC256(key);
        JWTVerifier jwtVerifier = JWT.require(algorithm).build();
        // 验证JWT令牌
        DecodedJWT verify = jwtVerifier.verify(jwt);
        // 获取JWT的所有声明
        Map<String, Claim> claims = verify.getClaims();
        // 返回用户名
        return claims.get("name").asString();
    }

}