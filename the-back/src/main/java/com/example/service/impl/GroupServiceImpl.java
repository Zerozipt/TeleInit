package com.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.entity.dto.Account;
import com.example.entity.dto.Group;
import com.example.entity.dto.Group_member;
import com.example.entity.dto.OutboxEvent;
import com.example.entity.dto.GroupInvitation;
import com.example.entity.vo.response.GroupDetailResponse;
import com.example.entity.vo.response.GroupMemberResponse;
import com.example.mapper.AccountMapper;
import com.example.mapper.GroupMapper;
import com.example.mapper.Group_memberMapper;
import com.example.mapper.GroupInvitationMapper;
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
import java.util.HashMap;
import org.springframework.messaging.simp.SimpMessagingTemplate;


@Service
public class GroupServiceImpl implements GroupService {

    @Resource
    GroupMapper groupMapper;
    @Resource
    Group_memberMapper groupMemberMapper;
    @Resource
    AccountMapper accountMapper;
    @Resource
    GroupInvitationMapper groupInvitationMapper;
    @Autowired
    private RedisService redisService;
    @Autowired
    private GroupCacheService groupCacheService;
    @Autowired
    private OutboxEventService outboxEventService;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
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
            // 获取群组信息（用于通知）
            Group group = groupMapper.selectById(groupId);
            String groupName = group != null ? group.getName() : "未知群组";
            
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
                
                // 发送WebSocket通知给退出的用户
                try {
                    Map<String, Object> exitNotification = new HashMap<>();
                    exitNotification.put("type", "GROUP_EXIT_SUCCESS");
                    exitNotification.put("groupId", groupId);
                    exitNotification.put("groupName", groupName);
                    exitNotification.put("message", "您已成功退出群组: " + groupName);
                    
                    messagingTemplate.convertAndSendToUser(
                        String.valueOf(userId),
                        "/queue/notifications",
                        exitNotification
                    );
                    
                    logger.info("已向用户 {} 发送退出群组成功通知, 群组: {}", userId, groupName);
                } catch (Exception e) {
                    logger.error("发送退出群组通知失败: {}", e.getMessage(), e);
                }
                
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

    // ========== 群主管理功能实现 ==========
    
    @Override
    @Transactional
    public boolean removeMember(String groupId, int memberId) {
        try {
            if (groupId == null || groupId.trim().isEmpty() || memberId <= 0) {
                logger.warn("移除群成员参数无效: groupId={}, memberId={}", groupId, memberId);
                return false;
            }
            
            // 验证要移除的用户是否为群成员
            QueryWrapper<Group_member> checkQuery = new QueryWrapper<>();
            checkQuery.eq("group_id", groupId).eq("user_id", memberId);
            Group_member member = groupMemberMapper.selectOne(checkQuery);
            
            if (member == null) {
                logger.warn("尝试移除不存在的群成员: groupId={}, memberId={}", groupId, memberId);
                return false;
            }
            
            // 不能移除群主
            if ("CREATOR".equals(member.getRole())) {
                logger.warn("尝试移除群主: groupId={}, memberId={}", groupId, memberId);
                return false;
            }
            
            // 删除群成员记录
            int deleted = groupMemberMapper.delete(checkQuery);
            
            if (deleted > 0) {
                // 更彻底地清理该用户在该群组的所有邀请记录，避免重新加入时的唯一约束冲突
                try {
                    // 清理该用户作为被邀请者的所有记录
                    QueryWrapper<GroupInvitation> inviteeDeleteQuery = new QueryWrapper<>();
                    inviteeDeleteQuery.eq("group_id", groupId).eq("invitee_id", memberId);
                    int inviteeRecordsDeleted = groupInvitationMapper.delete(inviteeDeleteQuery);
                    
                    // 清理该用户作为邀请者的所有记录
                    QueryWrapper<GroupInvitation> inviterDeleteQuery = new QueryWrapper<>();
                    inviterDeleteQuery.eq("group_id", groupId).eq("inviter_id", memberId);
                    int inviterRecordsDeleted = groupInvitationMapper.delete(inviterDeleteQuery);
                    
                    logger.info("清理邀请记录: groupId={}, memberId={}, 作为被邀请者删除={}, 作为邀请者删除={}", 
                               groupId, memberId, inviteeRecordsDeleted, inviterRecordsDeleted);
                } catch (Exception e) {
                    logger.warn("清理邀请记录时发生错误，但成员已移除: groupId={}, memberId={}", groupId, memberId, e);
                }
                
                // 清除相关缓存
                clearUserGroupCache(memberId);
                groupCacheService.invalidateGroupDetail(groupId);
                
                // 记录操作事件
                String eventPayload = JSON.toJSONString(Map.of(
                    "groupId", groupId,
                    "memberId", memberId,
                    "memberName", member.getGroupName(), // 这里实际应该是用户名，但现在用群名代替
                    "action", "GROUP_MEMBER_REMOVED_BY_ADMIN"
                ));
                outboxEventService.createEvent(
                    OutboxEvent.EventTypes.GROUP_MEMBER_REMOVED, 
                    groupId + ":" + memberId, 
                    eventPayload
                );
                
                logger.info("群成员被移除: groupId={}, memberId={}, memberRole={}", 
                           groupId, memberId, member.getRole());
                return true;
            } else {
                logger.warn("移除群成员操作未执行: groupId={}, memberId={}", groupId, memberId);
                return false;
            }
        } catch (Exception e) {
            logger.error("移除群成员失败: groupId={}, memberId={}", groupId, memberId, e);
            return false;
        }
    }
    
    @Override
    @Transactional
    public boolean updateGroupName(String groupId, String newName) {
        try {
            if (groupId == null || groupId.trim().isEmpty()) {
                logger.warn("更新群组名称时群组ID无效: groupId={}", groupId);
                return false;
            }
            
            if (newName == null || newName.trim().isEmpty()) {
                logger.warn("更新群组名称时新名称无效: newName={}", newName);
                return false;
            }
            
            String trimmedName = newName.trim();
            
            // 检查群组是否存在
            Group group = groupMapper.selectById(groupId);
            if (group == null) {
                logger.warn("尝试更新不存在的群组: groupId={}", groupId);
                return false;
            }
            
            String oldName = group.getName();
            
            // 如果名称没有变化，直接返回成功
            if (trimmedName.equals(oldName)) {
                logger.info("群组名称未发生变化: groupId={}, name={}", groupId, trimmedName);
                return true;
            }
            
            // 检查新名称是否已被其他群组使用
            QueryWrapper<Group> nameCheckQuery = new QueryWrapper<>();
            nameCheckQuery.eq("name", trimmedName).ne("group_id", groupId);
            if (groupMapper.exists(nameCheckQuery)) {
                logger.warn("群组名称已被使用: newName={}", trimmedName);
                return false;
            }
            
            // 更新群组表中的名称
            group.setName(trimmedName);
            int groupUpdated = groupMapper.updateById(group);
            
            if (groupUpdated > 0) {
                // 同时更新group_members表中的群组名称以保持一致性
                Group_member updateMember = new Group_member();
                updateMember.setGroupName(trimmedName);
                QueryWrapper<Group_member> memberUpdateQuery = new QueryWrapper<>();
                memberUpdateQuery.eq("group_id", groupId);
                groupMemberMapper.update(updateMember, memberUpdateQuery);
                
                // 清除相关缓存
                groupCacheService.invalidateGroupDetail(groupId);
                
                // 记录操作事件
                String eventPayload = JSON.toJSONString(Map.of(
                    "groupId", groupId,
                    "oldName", oldName,
                    "newName", trimmedName,
                    "action", "GROUP_NAME_CHANGED"
                ));
                outboxEventService.createEvent(
                    OutboxEvent.EventTypes.GROUP_NAME_CHANGED, 
                    groupId, 
                    eventPayload
                );
                
                logger.info("群组名称更新成功: groupId={}, oldName={}, newName={}", 
                           groupId, oldName, trimmedName);
                return true;
            } else {
                logger.warn("更新群组名称失败: groupId={}, newName={}", groupId, trimmedName);
                return false;
            }
        } catch (DuplicateKeyException e) {
            logger.warn("群组名称已存在: newName={}", newName);
            return false;
        } catch (Exception e) {
            logger.error("更新群组名称失败: groupId={}, newName={}", groupId, newName, e);
            return false;
        }
    }
    
    @Override
    @Transactional
    public boolean dissolveGroup(String groupId) {
        try {
            if (groupId == null || groupId.trim().isEmpty()) {
                logger.warn("解散群组时群组ID无效: groupId={}", groupId);
                return false;
            }
            
            // 检查群组是否存在
            Group group = groupMapper.selectById(groupId);
            if (group == null) {
                logger.warn("尝试解散不存在的群组: groupId={}", groupId);
                return false;
            }
            
            // 获取所有群成员（用于后续通知）
            List<Group_member> allMembers = getAllGroupMembers(groupId);
            
            // 删除所有群成员记录
            QueryWrapper<Group_member> memberDeleteQuery = new QueryWrapper<>();
            memberDeleteQuery.eq("group_id", groupId);
            int membersDeleted = groupMemberMapper.delete(memberDeleteQuery);
            
            // 删除群组记录
            int groupDeleted = groupMapper.deleteById(groupId);
            
            if (groupDeleted > 0) {
                // 删除该群组的所有邀请记录
                QueryWrapper<GroupInvitation> invitationDeleteQuery = new QueryWrapper<>();
                invitationDeleteQuery.eq("group_id", groupId);
                int invitationsDeleted = groupInvitationMapper.delete(invitationDeleteQuery);
                logger.info("清理群组邀请记录: groupId={}, deletedCount={}", groupId, invitationsDeleted);
                
                // 清除所有相关缓存
                groupCacheService.invalidateGroupDetail(groupId);
                for (Group_member member : allMembers) {
                    clearUserGroupCache(member.getUserId());
                }
                
                // 记录解散事件
                String eventPayload = JSON.toJSONString(Map.of(
                    "groupId", groupId,
                    "groupName", group.getName(),
                    "memberCount", allMembers.size(),
                    "allMembers", allMembers,
                    "action", "GROUP_DISSOLVED"
                ));
                outboxEventService.createEvent(
                    OutboxEvent.EventTypes.GROUP_DISSOLVED, 
                    groupId, 
                    eventPayload
                );
                
                logger.info("群组解散成功: groupId={}, groupName={}, memberCount={}", 
                           groupId, group.getName(), allMembers.size());
                return true;
            } else {
                logger.warn("解散群组失败: groupId={}", groupId);
                return false;
            }
        } catch (Exception e) {
            logger.error("解散群组失败: groupId={}", groupId, e);
            return false;
        }
    }
    
    @Override
    public List<Group_member> getAllGroupMembers(String groupId) {
        try {
            if (groupId == null || groupId.trim().isEmpty()) {
                logger.warn("获取群组成员时群组ID无效: groupId={}", groupId);
                return List.of();
            }
            
            QueryWrapper<Group_member> query = new QueryWrapper<>();
            query.eq("group_id", groupId).orderByAsc("joined_at");
            
            List<Group_member> members = groupMemberMapper.selectList(query);
            logger.debug("获取群组所有成员: groupId={}, memberCount={}", groupId, members.size());
            
            return members;
        } catch (Exception e) {
            logger.error("获取群组成员失败: groupId={}", groupId, e);
            return List.of();
        }
    }
}
