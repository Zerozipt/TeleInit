package com.example.service.impl;

import com.example.entity.dto.PrivateChatMessage;
import com.example.entity.dto.Group_message;
import com.example.entity.vo.response.ChatMessage;
import com.example.mapper.Group_messageMapper;
import com.example.mapper.Private_messagesMapper;
import com.example.service.ChatCacheService;
import com.example.service.RedisService;
import com.example.service.AccountService;
import com.example.mapper.AccountMapper;
import com.example.entity.dto.Account;
import com.example.utils.RedisKeys;
import com.example.utils.Const;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;

@Service
public class ChatCacheServiceImpl implements ChatCacheService {

    private static final Duration EXPIRE = Duration.ofDays(Const.MESSAGE_EXPIRE_DAYS);

    // FIRST_EDIT: 添加原子化私聊缓存 Lua 脚本
    private static final RedisScript<Long> CACHE_PRIVATE_MESSAGE_SCRIPT = new DefaultRedisScript<>(
        """
        local dialogKey = KEYS[1]
        local recentSender = KEYS[2]
        local recentReceiver = KEYS[3]
        local unreadKey = KEYS[4]
        local listSender = KEYS[5]
        local listReceiver = KEYS[6]
        local message = ARGV[1]
        local ts = tonumber(ARGV[2])
        local ttl = tonumber(ARGV[3])
        local maxLen = tonumber(ARGV[4])
        redis.call('ZADD', dialogKey, ts, message)
        redis.call('EXPIRE', dialogKey, ttl)
        redis.call('ZADD', recentSender, ts, dialogKey)
        redis.call('EXPIRE', recentSender, ttl)
        redis.call('ZADD', recentReceiver, ts, dialogKey)
        redis.call('EXPIRE', recentReceiver, ttl)
        redis.call('HINCRBY', unreadKey, dialogKey, 1)
        redis.call('EXPIRE', unreadKey, ttl)
        redis.call('RPUSH', listSender, message)
        if maxLen > 0 then redis.call('LTRIM', listSender, -maxLen, -1) end
        redis.call('EXPIRE', listSender, ttl)
        redis.call('RPUSH', listReceiver, message)
        if maxLen > 0 then redis.call('LTRIM', listReceiver, -maxLen, -1) end
        redis.call('EXPIRE', listReceiver, ttl)
        return 1
        """,
        Long.class
    );

    @Autowired
    private RedisService redisService;

    @Autowired
    private Group_messageMapper group_messageMapper;

    @Autowired
    private Private_messagesMapper privateMessageMapper;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountMapper accountMapper;

    @Override
    public void cacheGroupMessage(String groupId, String messageJson) {
        String key = RedisKeys.CHAT_GROUP + groupId;
        // 保留最新1000条
        redisService.pushList(key, messageJson, 1000, EXPIRE);
    }

    @Override
    public List<Group_message> getGroupChatHistory(String groupId, int limit) {
        String key = RedisKeys.CHAT_GROUP + groupId;
        List<String> jsons = redisService.rangeList(key, 0, -1);
        List<Group_message> messages;
        if (jsons.isEmpty()) {
            // 回落数据库
            List<Group_message> dbList = group_messageMapper.selectList(
                    Wrappers.<Group_message>query().eq("groupId", groupId).orderByAsc("Create_at")
            );
            // 批量查询发送者名称，避免空列表导致 SQL 语法错误
            List<Integer> senderIds = dbList.stream()
                    .map(Group_message::getSenderId)
                    .distinct()
                    .collect(Collectors.toList());
            Map<Integer, String> nameMap = new HashMap<>();
            if (!senderIds.isEmpty()) {
                List<Account> accounts = accountMapper.selectBatchIds(senderIds);
                nameMap = accounts.stream()
                        .collect(Collectors.toMap(Account::getId, Account::getUsername));
            }
            messages = new ArrayList<>();
            for (Group_message gm : dbList) {
                gm.setSenderName(nameMap.get(gm.getSenderId()));
                String json = JSON.toJSONString(gm);
                redisService.pushList(key, json, 1000, EXPIRE);
                messages.add(gm);
            }
        } else {
            List<Group_message> cached = new ArrayList<>();
            for (String json : jsons) {
                try {
                    if (json.startsWith("{")) {
                        cached.add(JSON.parseObject(json, Group_message.class));
                    }
                } catch (JSONException ignore) {}
            }
            // 批量查询发送者名称，避免空列表导致 SQL 语法错误
            List<Integer> cachedSenderIds = cached.stream()
                    .map(Group_message::getSenderId)
                    .distinct()
                    .collect(Collectors.toList());
            Map<Integer, String> nameMapCached = new HashMap<>();
            if (!cachedSenderIds.isEmpty()) {
                List<Account> cachedAccounts = accountMapper.selectBatchIds(cachedSenderIds);
                nameMapCached = cachedAccounts.stream()
                        .collect(Collectors.toMap(Account::getId, Account::getUsername));
            }
            for (Group_message gm : cached) {
                gm.setSenderName(nameMapCached.get(gm.getSenderId()));
            }
            messages = cached;
        }
        // 截取最后 limit 条
        if (messages.size() > limit) {
            return messages.subList(messages.size() - limit, messages.size());
        }
        return messages;
    }

    @Override
    public void cachePrivateMessage(ChatMessage message) {
        int senderId = message.getSenderId();
        int receiverId = message.getReceiverId();
        String dialogId = senderId < receiverId ? senderId + ":" + receiverId : receiverId + ":" + senderId;
        String msgJson = JSON.toJSONString(message);
        long timestamp = message.getTimestamp().getTime();
        String dialogKey = RedisKeys.DIALOG_PRIV + dialogId;
        String recentKeySender = String.format(RedisKeys.USER_DIALOGS_RECENT, senderId);
        String recentKeyReceiver = String.format(RedisKeys.USER_DIALOGS_RECENT, receiverId);
        String unreadKey = String.format(RedisKeys.USER_UNREAD_PRIV, receiverId);
        String listKeySender = RedisKeys.CHAT_PRIVATE + senderId;
        String listKeyReceiver = RedisKeys.CHAT_PRIVATE + receiverId;
        // FIRST_EDIT: 使用 Lua 脚本原子执行所有 Redis 操作
        List<String> keys = List.of(dialogKey, recentKeySender, recentKeyReceiver, unreadKey, listKeySender, listKeyReceiver);
        Object[] args = new Object[]{msgJson, String.valueOf(timestamp), String.valueOf(EXPIRE.toSeconds()), "1000"};
        redisService.executeScript(CACHE_PRIVATE_MESSAGE_SCRIPT, keys, args);
    }

    @Override
    public List<PrivateChatMessage> getPrivateChatHistory(int userId, int limit) {
        String key = RedisKeys.CHAT_PRIVATE + userId;
        List<String> jsons = redisService.rangeList(key, 0, -1);
        List<PrivateChatMessage> messages = new ArrayList<>();
        if (jsons.isEmpty()) {
            // 回落数据库
            List<PrivateChatMessage> dbList = privateMessageMapper.selectList(
                    Wrappers.<PrivateChatMessage>query()
                            .eq("sender_id", userId)
                            .or().eq("receiver_id", userId)
                            .orderByDesc("created_at")
            );
            for (PrivateChatMessage pcm : dbList) {
                String json = JSON.toJSONString(pcm);
                redisService.pushList(key, json, 1000, EXPIRE);
                messages.add(pcm);
            }
        } else {
            for (String json : jsons) {
                try {
                    if (json.startsWith("[")) {
                        messages.addAll(JSON.parseArray(json, PrivateChatMessage.class));
                    } else {
                        messages.add(JSON.parseObject(json, PrivateChatMessage.class));
                    }
                } catch (JSONException e) {
                    // skip
                }
            }
        }
        // 保留最后 limit 条
        if (messages.size() > limit) {
            return messages.subList(messages.size() - limit, messages.size());
        }
        return messages;
    }
} 