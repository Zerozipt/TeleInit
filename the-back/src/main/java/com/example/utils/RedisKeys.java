package com.example.utils;

public class RedisKeys {
    // 全局前缀
    public static final String PREFIX = "teleinit:";

    // JWT 黑名单
    public static final String JWT_BLACKLIST = PREFIX + "jwt:blacklist:";

    // 邮箱验证码
    public static final String VERIFY_EMAIL = PREFIX + "verify:email:";

    // 限流 - 单次
    public static final String RATE_LIMIT_ONCE = PREFIX + "rate:once:";

    // 限流 - 窗口
    public static final String RATE_LIMIT_WINDOW = PREFIX + "rate:window:";

    // 在线状态
    public static final String ONLINE_STATUS = PREFIX + "user:online:";

    // 群聊消息列表（List）
    public static final String CHAT_GROUP = PREFIX + "chat:group:";

    // 私聊消息列表（List）
    public static final String CHAT_PRIVATE = PREFIX + "chat:private:";

    // 私聊对话（ZSet）
    public static final String DIALOG_PRIV = PREFIX + "dialog:priv:";

    // 用户最近对话（ZSet），格式 user:<userId>:dialogs:recent
    public static final String USER_DIALOGS_RECENT = PREFIX + "user:%s:dialogs:recent";

    // 用户未读私聊（Hash），格式 user:<userId>:unread:priv
    public static final String USER_UNREAD_PRIV = PREFIX + "user:%s:unread:priv";

    // 用户群组缓存
    public static final String USER_GROUPS = PREFIX + "user:groups:";

    // FIRST_EDIT 添加群组详情缓存键常量
    public static final String GROUP_DETAIL = PREFIX + "group:detail:";

    // FIRST_EDIT 添加限流 - 窗口计数前缀常量
    public static final String RATE_LIMIT_WINDOW_COUNTER = PREFIX + "rate:window:counter:";

    // FIRST_EDIT 添加限流 - 窗口阻塞前缀常量
    public static final String RATE_LIMIT_WINDOW_BLOCK = PREFIX + "rate:window:block:";

    // 消息过期天数，可使用 Const.MESSAGE_EXPIRE_DAYS
} 