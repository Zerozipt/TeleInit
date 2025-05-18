package com.example.service;

public interface RateLimiterService {
    /**
     * 单次限流（一次性锁定）
     * @param key 限流键
     * @param blockSeconds 锁定时长（秒）
     * @return 是否允许（true=允许，false=已被锁定）
     */
    boolean tryOnce(String key, int blockSeconds);

    /**
     * 滑动窗口限流，原子操作（基于 Lua 脚本）
     * @param id 唯一标识（如IP、用户）
     * @param limitCount 限制次数
     * @param blockSeconds 超过限制时的封禁时长（秒）
     * @param windowSeconds 计数周期（秒）
     * @return 是否允许（true=允许，false=阻止）
     */
    boolean tryWindow(String id, int limitCount, int blockSeconds, int windowSeconds);

    /**
     * 默认限流：60次/秒，封禁60秒
     * @param id 唯一标识
     * @return 是否允许
     */
    boolean tryDefault(String id);
} 