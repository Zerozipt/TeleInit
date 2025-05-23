package com.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.entity.dto.Account;
import com.example.entity.dto.Group;
import com.example.entity.dto.Group_member;
import com.example.entity.vo.response.GroupDetailResponse;
import com.example.entity.vo.response.GroupMemberResponse;
import com.example.mapper.AccountMapper;
import com.example.mapper.GroupMapper;
import com.example.mapper.Group_memberMapper;
import com.example.service.GroupService;
import com.example.utils.Const;
import com.example.utils.RedisKeys;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.service.RedisService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Date;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import com.example.service.GroupCacheService;


@Service
public class GroupServiceImpl implements GroupService {

    @Resource
    GroupMapper groupMapper;
    @Resource
    Group_memberMapper groupMemberMapper;
    @Resource
    AccountMapper accountMapper;
    @Autowired
    private RedisService redisService;
    @Autowired
    private GroupCacheService groupCacheService;
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
        creatorMember.setGroupName(groupName);
        int memberInserted = groupMemberMapper.insert(creatorMember);
        groupCacheService.invalidateUserGroups(creatorId);
        groupCacheService.invalidateGroupDetail(newGroup.getGroupId());
        if (memberInserted <= 0) {
            // 如果添加成员失败，需要回滚群组创建操作 (通过 @Transactional 实现)
            throw new RuntimeException("将创建者添加到群成员失败");
        }

        // 返回创建成功的群组信息
        return newGroup;
    }

    @Override
    @Transactional
    public Group_member joinGroupById(String groupId, int userId) {
        if (groupId == null || groupId.trim().isEmpty()) {
            throw new IllegalArgumentException("群组ID不能为空");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        
        // 根据群组 ID 获取群组信息
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new RuntimeException("加入失败：群聊不存在 (ID: " + groupId + ")");
        }
        
        // 检查用户是否已在群组中
        QueryWrapper<Group_member> memberCheck = new QueryWrapper<>();
        memberCheck.eq("group_id", groupId).eq("user_id", userId).last("LIMIT 1");
        if (groupMemberMapper.exists(memberCheck)) {
            throw new RuntimeException("用户已在该群聊中");
        }
        
        // 添加新成员
        Group_member newMember = new Group_member();
        newMember.setGroupId(groupId);
        newMember.setUserId(userId);
        newMember.setJoinedAt(new Date());
        newMember.setRole("MEMBER");
        newMember.setGroupName(group.getName());

        int result = groupMemberMapper.insert(newMember);
        groupCacheService.invalidateUserGroups(userId);
        groupCacheService.invalidateGroupDetail(groupId);
        if (result <= 0) {
            throw new RuntimeException("加入群聊数据库操作失败");
        }
        return newMember;
    }

    @Override
    @Transactional
    public Group_member joinGroup(String groupId, int userId) {
        // 使用 joinGroupById 方法实现通过 ID 加入群聊
        return joinGroupById(groupId, userId);
    }

    /**
     * 清除指定用户的群组列表缓存
     * @param userId 用户ID
     */
    private void clearUserGroupCache(int userId) {
        String cacheKey = RedisKeys.USER_GROUPS + userId;
        try {
            Boolean deleted = redisService.delete(cacheKey);
            if (Boolean.TRUE.equals(deleted)) {
                logger.info("Successfully deleted group cache for user {} after operation.", userId);
            } else {
                logger.info("Group cache key {} not found or already deleted for user {} after operation.", cacheKey, userId);
            }
        } catch (Exception e) {
            logger.error("Redis error when deleting group cache key {} for user {}: {}", cacheKey, userId, e.getMessage(), e);
        }
    }

    @Override
    public List<Group> getGroupByName(String groupName) {
        //从mysql中获取groupName对应的group,使用相似查询
        QueryWrapper<Group> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("name", "%" + groupName + "%");
        return groupMapper.selectList(queryWrapper);
    }

    @Override
    public List<Group_member> getGroupMembers(int userId) {
        // FIRST_EDIT 委托缓存服务获取用户群组列表
        return groupCacheService.getUserGroups(userId);
    }

    @Override
    public GroupDetailResponse getGroupDetail(String groupId) {
        // FIRST_EDIT 委托缓存服务获取群组详情
        return groupCacheService.getGroupDetail(groupId);
    }
    
    @Override
    public boolean isGroupMember(String groupId, int userId) {
        if (groupId == null || userId <= 0) {
            return false;
        }
        
        QueryWrapper<Group_member> query = new QueryWrapper<>();
        query.eq("group_id", groupId).eq("user_id", userId);
        
        return groupMemberMapper.exists(query);
    }

    @Override
    @Transactional
    public boolean leaveGroup(String groupId, int userId) {
        try {
            // 从群组成员中删除
            int deleted = groupMemberMapper.delete(
                new QueryWrapper<Group_member>()
                    .eq("group_id", groupId)
                    .eq("user_id", userId)
            );
            // FIRST_EDIT 替换为缓存失效
            groupCacheService.invalidateUserGroups(userId);
            groupCacheService.invalidateGroupDetail(groupId);
            if (deleted > 0) {
                logger.info("用户 {} 已退出群组 {}", userId, groupId);
                return true;
            } else {
                logger.warn("退出群组未执行或找不到记录: userId={}, groupId={}", userId, groupId);
                return false;
            }
        } catch (Exception e) {
            logger.error("退出群组失败: userId={}, groupId={}", userId, groupId, e);
            return false;
        }
    }
}
