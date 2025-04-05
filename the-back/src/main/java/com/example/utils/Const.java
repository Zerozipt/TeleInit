package com.example.utils;

public class Const {
    public static final String JWT_BLACK_LIST = "jwt_black_list";

    public static final String VERIFY_EMAIL_LIMIT = "verify_email:limit";
    public static final String VERIFY_EMAIL_Data = "verify_email:data";
    public static final String FLOW_LIMIT_COUNTER = "flow_limit:counter";
    public static final String FLOW_LIMIT_BLOCK = "flow_limit:block";

    public static final String FRIENDS_WITH_USER_ID = "friends_with_user_id";
    
    public static final String PRIVATE_CHAT_KEY = "chat:private:";
    public static final String GROUP_CHAT_KEY = "chat:group:";
    public static final int MESSAGE_EXPIRE_DAYS = 7; // 消息保存7天

    public static final int ORDER_CORS = -102;
    public static final int ORDER_FLOW_LIMIT = -101;


    public static final int FLOW_LIMIT_COUNT = 60;
}
