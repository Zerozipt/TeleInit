/*
 Navicat Premium Dump SQL

 Source Server         : 腾讯云mysql
 Source Server Type    : MySQL
 Source Server Version : 80404 (8.4.4)
 Source Host           : 175.178.18.145:13306
 Source Schema         : test

 Target Server Type    : MySQL
 Target Server Version : 80404 (8.4.4)
 File Encoding         : 65001

 Date: 24/05/2025 01:40:29
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for cache_warmup_tasks
-- ----------------------------
DROP TABLE IF EXISTS `cache_warmup_tasks`;
CREATE TABLE `cache_warmup_tasks`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `cache_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '缓存键',
  `cache_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '缓存类型',
  `entity_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '实体ID',
  `priority` int NULL DEFAULT 5 COMMENT '优先级(1-10)',
  `status` enum('PENDING','PROCESSING','COMPLETED','FAILED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'PENDING' COMMENT '任务状态',
  `retry_count` int NULL DEFAULT 0 COMMENT '重试次数',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `processed_at` timestamp NULL DEFAULT NULL COMMENT '处理时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_cache_key`(`cache_key` ASC) USING BTREE,
  INDEX `idx_status_priority`(`status` ASC, `priority` ASC, `created_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '缓存预热任务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for db_account
-- ----------------------------
DROP TABLE IF EXISTS `db_account`;
CREATE TABLE `db_account`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `role` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `register_time` datetime NULL DEFAULT NULL,
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `Idinex`(`id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for friends
-- ----------------------------
DROP TABLE IF EXISTS `friends`;
CREATE TABLE `friends`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `the_first_user_id` int NOT NULL,
  `the_second_user_id` int NOT NULL,
  `STATUS` enum('requested','accepted','rejected','deleted') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'requested',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `version` int NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `FriendQuery`(`the_first_user_id` ASC) USING BTREE,
  INDEX `FriendQueryBySec`(`the_second_user_id` ASC) USING BTREE,
  UNIQUE INDEX `uk_friend_request`(`the_first_user_id` ASC, `the_second_user_id` ASC, `STATUS` ASC) USING BTREE,
  INDEX `idx_friends_status`(`STATUS` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_friends_user_pair`(`the_first_user_id` ASC, `the_second_user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 17 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for group
-- ----------------------------
DROP TABLE IF EXISTS `group`;
CREATE TABLE `group`  (
  `group_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '群组名称',
  `creator_id` int NOT NULL COMMENT '创建者用户ID',
  `create_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `version` int NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`group_id`) USING BTREE,
  INDEX `idx_creator`(`creator_id` ASC) USING BTREE COMMENT '创建者索引',
  UNIQUE INDEX `uk_group_name`(`name` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '群组信息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for group_invitations
-- ----------------------------
DROP TABLE IF EXISTS `group_invitations`;
CREATE TABLE `group_invitations`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '群组ID',
  `inviter_id` int NOT NULL COMMENT '邀请者ID',
  `invitee_id` int NOT NULL COMMENT '被邀请者ID',
  `status` enum('pending','accepted','rejected') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'pending' COMMENT '邀请状态',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_invitee_status`(`invitee_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_group_inviter`(`group_id` ASC, `inviter_id` ASC) USING BTREE,
  UNIQUE INDEX `uk_group_invitation`(`group_id` ASC, `inviter_id` ASC, `invitee_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_group_invitations_status`(`status` ASC, `created_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 8 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '群组邀请表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for group_members
-- ----------------------------
DROP TABLE IF EXISTS `group_members`;
CREATE TABLE `group_members`  (
  `user_id` int NOT NULL COMMENT '用户ID',
  `group_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '群组ID',
  `joined_at` datetime NOT NULL COMMENT '加入时间',
  `role` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '成员角色',
  `groupname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '群组名称',
  `version` int NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`user_id`, `group_id`) USING BTREE,
  INDEX `idx_group_id`(`group_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  UNIQUE INDEX `uk_user_group`(`user_id` ASC, `group_id` ASC) USING BTREE,
  INDEX `idx_group_members_user`(`user_id` ASC, `joined_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '群组成员表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for group_messages
-- ----------------------------
DROP TABLE IF EXISTS `group_messages`;
CREATE TABLE `group_messages`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `groupId` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '群组ID',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '消息内容',
  `SenderId` int NULL DEFAULT NULL COMMENT '发送者ID',
  `Create_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `Content_type` smallint NOT NULL COMMENT '内容类型(0:文本 1:图片 2:视频等)',
  `File_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件URL',
  `File_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件原始名称',
  `File_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件MIME类型',
  `File_size` bigint NULL DEFAULT NULL COMMENT '文件大小(字节)',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_group`(`groupId` ASC) USING BTREE,
  INDEX `idx_sender`(`SenderId` ASC) USING BTREE,
  INDEX `idx_time`(`Create_at` ASC) USING BTREE,
  INDEX `idx_group_messages_group_time`(`groupId` ASC, `Create_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1914960728448692238 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '群组消息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for outbox_events
-- ----------------------------
DROP TABLE IF EXISTS `outbox_events`;
CREATE TABLE `outbox_events`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `entity_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `status` enum('PENDING','PROCESSING','SENT','FAILED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'PENDING',
  `retry_count` int NULL DEFAULT 0,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `processed_at` timestamp NULL DEFAULT NULL,
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_status_created`(`status` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_entity_type`(`entity_id` ASC, `event_type` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for private_messages
-- ----------------------------
DROP TABLE IF EXISTS `private_messages`;
CREATE TABLE `private_messages`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sender_id` int NOT NULL,
  `receiver_id` int NOT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `is_read` tinyint(1) NULL DEFAULT 0 COMMENT '是否已读',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `file_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件原始名称',
  `file_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件MIME类型',
  `file_size` bigint NULL DEFAULT NULL COMMENT '文件大小(字节)',
  `message_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '消息类型(TEXT/FILE/IMAGE/VIDEO/AUDIO)',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `messageQueryBySender`(`sender_id` ASC) USING BTREE,
  INDEX `messageQueryByReciver`(`receiver_id` ASC) USING BTREE,
  INDEX `idx_private_messages_conversation`(`sender_id` ASC, `receiver_id` ASC, `created_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1918598219437199426 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Triggers structure for table friends
-- ----------------------------
DROP TRIGGER IF EXISTS `tr_friends_version_update`;
delimiter ;;
CREATE TRIGGER `tr_friends_version_update` BEFORE UPDATE ON `friends` FOR EACH ROW BEGIN
    SET NEW.version = OLD.version + 1;
END
;;
delimiter ;

-- ----------------------------
-- Triggers structure for table group
-- ----------------------------
DROP TRIGGER IF EXISTS `tr_group_version_update`;
delimiter ;;
CREATE TRIGGER `tr_group_version_update` BEFORE UPDATE ON `group` FOR EACH ROW BEGIN
    SET NEW.version = OLD.version + 1;
END
;;
delimiter ;

-- ----------------------------
-- Triggers structure for table group_members
-- ----------------------------
DROP TRIGGER IF EXISTS `tr_group_members_version_update`;
delimiter ;;
CREATE TRIGGER `tr_group_members_version_update` BEFORE UPDATE ON `group_members` FOR EACH ROW BEGIN
    SET NEW.version = OLD.version + 1;
END
;;
delimiter ;

SET FOREIGN_KEY_CHECKS = 1;
