# 类Telegram聊天通讯系统数据库设计

## 核心数据表设计

### 1. 会话表 (conversation)
```sql
CREATE TABLE `conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `type` tinyint NOT NULL COMMENT '会话类型：1-私聊，2-群聊',
  `name` varchar(100) DEFAULT NULL COMMENT '会话名称(群聊时使用)',
  `avatar` varchar(255) DEFAULT NULL COMMENT '会话头像(群聊时使用)',
  `creator_id` bigint DEFAULT NULL COMMENT '创建者ID(群聊时使用)',
  `announcement` text DEFAULT NULL COMMENT '群公告(群聊时使用)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_creator_id` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';
```

### 2. 会话成员表 (conversation_member)
```sql
CREATE TABLE `conversation_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `conversation_id` bigint NOT NULL COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `alias` varchar(50) DEFAULT NULL COMMENT '在会话中的别名',
  `role` tinyint NOT NULL DEFAULT '0' COMMENT '成员角色：0-普通成员，1-管理员，2-群主',
  `mute` tinyint NOT NULL DEFAULT '0' COMMENT '是否免打扰：0-否，1-是',
  `last_read_message_id` bigint DEFAULT NULL COMMENT '最后已读消息ID',
  `joined_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversation_user` (`conversation_id`,`user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话成员表';
```

### 3. 消息表 (message)
```sql
CREATE TABLE `message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `conversation_id` bigint NOT NULL COMMENT '会话ID',
  `sender_id` bigint NOT NULL COMMENT '发送者ID',
  `type` tinyint NOT NULL COMMENT '消息类型：1-文本，2-图片，3-视频，4-语音，5-文件，6-位置，7-系统消息',
  `content` text NOT NULL COMMENT '消息内容',
  `reply_to` bigint DEFAULT NULL COMMENT '回复消息ID',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '消息状态：1-发送中，2-已发送，3-发送失败',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除：0-否，1-是',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversation_id` (`conversation_id`),
  KEY `idx_sender_id` (`sender_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';
```

### 4. 消息阅读状态表 (message_read_status)
```sql
CREATE TABLE `message_read_status` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `message_id` bigint NOT NULL COMMENT '消息ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `read_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_user` (`message_id`,`user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息阅读状态表';
```

### 5. 好友关系表 (friend_relationship)
```sql
CREATE TABLE `friend_relationship` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `friend_id` bigint NOT NULL COMMENT '好友ID',
  `remark` varchar(50) DEFAULT NULL COMMENT '备注名',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0-待接受，1-已接受，2-已拒绝，3-已屏蔽',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_friend` (`user_id`,`friend_id`),
  KEY `idx_friend_id` (`friend_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友关系表';
```

### 6. 媒体文件表 (media_file)
```sql
CREATE TABLE `media_file` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  `message_id` bigint NOT NULL COMMENT '关联的消息ID',
  `type` tinyint NOT NULL COMMENT '文件类型：1-图片，2-视频，3-语音，4-文件',
  `original_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `file_path` varchar(255) NOT NULL COMMENT '文件路径',
  `file_size` bigint NOT NULL COMMENT '文件大小(字节)',
  `mime_type` varchar(100) DEFAULT NULL COMMENT '文件MIME类型',
  `duration` int DEFAULT NULL COMMENT '媒体时长(秒)',
  `width` int DEFAULT NULL COMMENT '图片/视频宽度',
  `height` int DEFAULT NULL COMMENT '图片/视频高度',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_message_id` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体文件表';
```

### 7. 用户状态表 (user_status)
```sql
CREATE TABLE `user_status` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0-离线，1-在线，2-离开',
  `last_active_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
  `device_info` varchar(255) DEFAULT NULL COMMENT '设备信息',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户状态表';
```

## 设计说明

1. **会话设计**：
   - 采用会话(conversation)作为核心概念，可以是私聊或群聊
   - 私聊和群聊统一管理，减少表的数量和复杂度
   - 群聊需要额外的群组信息，如名称、头像等

2. **消息设计**：
   - 支持多种消息类型：文本、图片、视频、语音、文件等
   - 消息表只存储基本信息，媒体文件单独存储
   - 支持消息回复功能

3. **已读状态**：
   - 为群聊场景设计了消息阅读状态表
   - 私聊可以通过会话成员表的last_read_message_id字段判断已读状态

4. **好友关系**：
   - 支持单向好友申请和双向确认
   - 包含备注名和好友状态管理

5. **用户状态**：
   - 记录用户在线状态和最后活跃时间
   - 支持多设备登录情况

## 数据表关联关系

- 会话表(conversation) 1:n 会话成员表(conversation_member)
- 会话表(conversation) 1:n 消息表(message)
- 消息表(message) 1:n 消息阅读状态表(message_read_status)
- 消息表(message) 1:n 媒体文件表(media_file)
- 用户与好友关系表(friend_relationship)是自关联表

## 扩展建议

1. **考虑分表**：当消息量大时，可按照会话ID或时间范围进行消息表分表
2. **缓存应用**：用户状态、最近会话等信息可考虑使用Redis缓存
3. **消息队列**：消息发送可通过消息队列处理，提高系统吞吐量
4. **WebSocket**：实时通讯建议使用WebSocket，可在用户状态表中记录连接信息

您可以根据实际需求对这个设计进行适当调整和扩展。如果有特定功能需求，可以进一步完善设计。