package com.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.entity.dto.Account;
import com.example.entity.dto.Group;
import com.example.entity.dto.Group_member;
import com.example.entity.dto.OutboxEvent;
import com.example.entity.vo.response.GroupDetailResponse;
import com.example.entity.vo.response.GroupMemberResponse;
import com.example.mapper.AccountMapper;
import com.example.mapper.GroupMapper;
import com.example.mapper.Group_memberMapper;
import com.example.service.GroupService;
import com.example.service.OutboxEventService;
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
import org.springframework.dao.DuplicateKeyException;
import com.alibaba.fastjson2.JSON;
import java.util.Map;


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
    @Autowired
    private OutboxEventService outboxEventService;
    
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
        
        try {
            // P0优化：直接创建，让数据库唯一约束处理重复名称
            Group newGroup = new Group();
            newGroup.setGroupId(UUID.randomUUID().toString());
            newGroup.setName(groupName);
            newGroup.setCreatorId(creatorId);
            newGroup.setCreate_at(new Date());

            int groupInserted = groupMapper.insert(newGroup);
            if (groupInserted <= 0) {
                throw new RuntimeException("创建群聊失败");
            }

            // 将创建者添加为群成员
            Group_member creatorMember = new Group_member();
            creatorMember.setGroupId(newGroup.getGroupId());
            creatorMember.setUserId(creatorId);
            creatorMember.setJoinedAt(new Date());
            creatorMember.setRole("CREATOR");
            creatorMember.setGroupName(groupName);
            
            int memberInserted = groupMemberMapper.insert(creatorMember);
            if (memberInserted <= 0) {
                throw new RuntimeException("将创建者添加到群成员失败");
            }
            
            // P1优化：使用本地消息表处理缓存失效，确保最终一致性
            String eventPayload = JSON.toJSONString(Map.of(
                "groupId", newGroup.getGroupId(),
                "userId", creatorId,
                "action", "GROUP_CREATED"
            ));
            outboxEventService.createEvent(
                OutboxEvent.EventTypes.GROUP_CREATED, 
                newGroup.getGroupId(), 
                eventPayload
            );

            logger.info("群组创建成功: groupId={}, name={}, creatorId={}", 
                       newGroup.getGroupId(), groupName, creatorId);
            return newGroup;
            
        } catch (DuplicateKeyException e) {
            logger.warn("群聊名称已存在: {}", groupName);
            throw new IllegalArgumentException("群聊名称 '" + groupName + "' 已被使用，请选择其他名称");
        } catch (Exception e) {
            logger.error("创建群组失败: groupName={}, creatorId={}", groupName, creatorId, e);
            throw e;
        }
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
        
        try {
            // 根据群组 ID 获取群组信息
            Group group = groupMapper.selectById(groupId);
            if (group == null) {
                throw new RuntimeException("加入失败：群聊不存在 (ID: " + groupId + ")");
            }
            
            // P0优化：直接插入，让数据库唯一约束处理重复加入
            Group_member newMember = new Group_member();
            newMember.setGroupId(groupId);
            newMember.setUserId(userId);
            newMember.setJoinedAt(new Date());
            newMember.setRole("MEMBER");
            newMember.setGroupName(group.getName());

            int result = groupMemberMapper.insert(newMember);
            if (result <= 0) {
                throw new RuntimeException("加入群聊数据库操作失败");
            }
            
            // P1优化：使用本地消息表处理缓存失效
            String eventPayload = JSON.toJSONString(Map.of(
                "groupId", groupId,
                "userId", userId,
                "action", "GROUP_MEMBER_ADDED"
            ));
            outboxEventService.createEvent(
                OutboxEvent.EventTypes.GROUP_MEMBER_ADDED, 
                groupId + ":" + userId, 
                eventPayload
            );
            
            logger.info("用户加入群组成功: groupId={}, userId={}", groupId, userId);
            return newMember;
            
        } catch (DuplicateKeyException e) {
            logger.warn("用户已在该群聊中: groupId={}, userId={}", groupId, userId);
            throw new RuntimeException("用户已在该群聊中");
        } catch (Exception e) {
            logger.error("加入群组失败: groupId={}, userId={}", groupId, userId, e);
            throw e;
        }
    }

    @Override
    @Transactional
    public Group_member joinGroup(String groupId, int userId) {
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
        QueryWrapper<Group> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("name", "%" + groupName + "%");
        return groupMapper.selectList(queryWrapper);
    }

    @Override
    public List<Group_member> getGroupMembers(int userId) {
        return groupCacheService.getUserGroups(userId);
    }

    @Override
    public GroupDetailResponse getGroupDetail(String groupId) {
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
            int deleted = groupMemberMapper.delete(
                new QueryWrapper<Group_member>()
                    .eq("group_id", groupId)
                    .eq("user_id", userId)
            );
            
            if (deleted > 0) {
                // P1优化：使用本地消息表处理缓存失效
                String eventPayload = JSON.toJSONString(Map.of(
                    "groupId", groupId,
                    "userId", userId,
                    "action", "GROUP_MEMBER_REMOVED"
                ));
                outboxEventService.createEvent(
                    OutboxEvent.EventTypes.GROUP_MEMBER_REMOVED, 
                    groupId + ":" + userId, 
                    eventPayload
                );
                
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
