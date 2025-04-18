package com.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.entity.dto.Group;
import com.example.entity.dto.Group_member;
import com.example.mapper.GroupMapper;
import com.example.mapper.Group_memberMapper;
import com.example.service.GroupService;
import com.example.utils.Const;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Date;
import java.util.UUID;

@Service
public class GroupServiceImpl implements GroupService {

    @Resource
    GroupMapper groupMapper;
    @Resource
    Group_memberMapper groupMemberMapper;
    @Resource
    StringRedisTemplate stringRedisTemplate;
    private static final Logger logger = LoggerFactory.getLogger(GroupServiceImpl.class);


    @Override
    @Transactional // 保证创建群组和添加创建者这两个操作是原子性的
    public Group createGroup(String groupName, int creatorId) {
        if (groupName == null || groupName.trim().isEmpty()) {
            throw new IllegalArgumentException("群聊名称不能为空");
        }
        if (creatorId <= 0) {
            throw new IllegalArgumentException("创建者ID不能为空");
        }
        // --- 开始检查名称唯一性 ---
        QueryWrapper<Group> checkNameWrapper = new QueryWrapper<>();
        checkNameWrapper.eq("name", groupName); // 假设数据库列名为 'name'
        if (groupMapper.exists(checkNameWrapper)) {
            // 如果名称已存在，抛出异常，阻止创建
            throw new IllegalArgumentException("创建失败：群聊名称 '" + groupName + "' 已被使用");
        }
        // --- 结束检查名称唯一性 ---

        // 1. 创建群组基本信息
        Group newGroup = new Group();
        newGroup.setGroupId(UUID.randomUUID().toString()); // 生成唯一群组ID
        newGroup.setName(groupName);
        newGroup.setCreatorId(creatorId);
        newGroup.setCreate_at(new Date()); // 设置创建时间

        int groupInserted = groupMapper.insert(newGroup);
        if (groupInserted <= 0) {
            throw new RuntimeException("创建群聊失败");
        }

        // 2. 将创建者添加为群成员 (通常是管理员或群主)
        Group_member creatorMember = new Group_member();
        creatorMember.setGroupId(newGroup.getGroupId());
        creatorMember.setUserId(creatorId);
        creatorMember.setJoinedAt(new Date()); // 格式化加入时间
        creatorMember.setRole("CREATOR"); // 或者 "ADMIN", "OWNER" 等

        int memberInserted = groupMemberMapper.insert(creatorMember);
        clearUserGroupCache(creatorId);
        if (memberInserted <= 0) {
            // 如果添加成员失败，需要回滚群组创建操作 (通过 @Transactional 实现)
            throw new RuntimeException("将创建者添加到群成员失败");
        }

        // 返回创建成功的群组信息
        return newGroup;
    }

    @Override
    @Transactional // 保证查询和插入是原子性的
    public Group_member joinGroup(String groupName, int userId) { // 1. 参数改为 groupName
        if (groupName == null || groupName.trim().isEmpty()) {
            throw new IllegalArgumentException("群聊名称不能为空"); // 2. 更新验证信息
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        // 3. 根据群聊名称查找群组 (假设群聊名称是唯一的)
        QueryWrapper<Group> groupQueryWrapper = new QueryWrapper<>();
        groupQueryWrapper.eq("name", groupName); // 假设数据库中群名称列为 'name'
        Group group = groupMapper.selectOne(groupQueryWrapper); // 使用 selectOne 期望只找到一个

        if (group == null) {
            // 4. 更新群聊不存在的错误信息
            throw new RuntimeException("加入失败：群聊不存在 (名称: " + groupName + ")");
        }

        // 5. 获取找到的群组的实际 groupId (UUID)
        String actualGroupId = group.getGroupId();

        // 6. 检查用户是否已在群组中 (使用实际的 groupId 和 userId)
        QueryWrapper<Group_member> memberCheckWrapper = new QueryWrapper<>();
        // 注意：这里应该用 group_id 列，而不是 group_name
        memberCheckWrapper.eq("group_id", actualGroupId).eq("user_id", userId).last("LIMIT 1");
        boolean alreadyMember = groupMemberMapper.exists(memberCheckWrapper); // 确认你的 GroupMemberMapper 变量名

        if (alreadyMember) {
            throw new RuntimeException("用户已在该群聊中");
        }

        // 7. 添加新成员，使用实际的 groupId
        Group_member newMember = new Group_member();
        newMember.setGroupId(actualGroupId); // 使用从 group 对象获取的 ID
        newMember.setUserId(userId);
        newMember.setJoinedAt(new Date()); // 推荐直接使用 Date 类型
        newMember.setRole("MEMBER"); // 默认角色为普通成员

        int inserted = groupMemberMapper.insert(newMember);
        clearUserGroupCache(userId);
        if (inserted <= 0) {
            // 可以考虑抛出更具体的异常，或者记录日志
            throw new RuntimeException("加入群聊数据库操作失败");
        }

        // TODO: (可选) 通过 WebSocket 通知群内其他成员有新用户加入

        return newMember;
    }

    /**
     * 清除指定用户的群组列表缓存
     * @param userId 用户ID
     */
    private void clearUserGroupCache(int userId) {
        String cacheKey = Const.GROUP_CHAT_KEY + ":" + userId;
        try {
            Boolean deleted = stringRedisTemplate.delete(cacheKey);
            if (Boolean.TRUE.equals(deleted)) {
                logger.info("Successfully deleted group cache for user {} after operation.", userId);
            } else {
                logger.info("Group cache key {} not found or already deleted for user {} after operation.", cacheKey, userId);
            }
        } catch (Exception e) {
            logger.error("Redis error when deleting group cache key {} for user {}: {}", cacheKey, userId, e.getMessage(), e);
        }
    }
}
