package com.example.utils;

import com.example.entity.dto.Group;
import com.example.entity.dto.Group_member;
import com.example.service.GroupService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.mapper.GroupMapper;
import com.example.mapper.Group_memberMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 群组权限验证工具类
 * 提供统一的群组权限验证方法
 */
@Component
public class GroupPermissionHelper {
    
    private static final Logger logger = LoggerFactory.getLogger(GroupPermissionHelper.class);
    
    @Resource
    private GroupMapper groupMapper;
    
    @Resource
    private Group_memberMapper groupMemberMapper;
    
    /**
     * 验证用户是否为群主
     * @param groupId 群组ID
     * @param userId 用户ID
     * @return 是否为群主
     */
    public boolean isGroupCreator(String groupId, int userId) {
        try {
            if (groupId == null || groupId.trim().isEmpty() || userId <= 0) {
                logger.warn("群组权限验证参数无效: groupId={}, userId={}", groupId, userId);
                return false;
            }
            
            Group group = groupMapper.selectById(groupId);
            boolean isCreator = group != null && group.getCreatorId() == userId;
            
            logger.debug("群主权限验证: groupId={}, userId={}, isCreator={}", groupId, userId, isCreator);
            return isCreator;
        } catch (Exception e) {
            logger.error("验证群主权限时发生错误: groupId={}, userId={}", groupId, userId, e);
            return false;
        }
    }
    
    /**
     * 验证用户是否为群管理员（包括群主）
     * @param groupId 群组ID
     * @param userId 用户ID
     * @return 是否为管理员
     */
    public boolean isGroupAdmin(String groupId, int userId) {
        try {
            if (groupId == null || groupId.trim().isEmpty() || userId <= 0) {
                logger.warn("群组管理员验证参数无效: groupId={}, userId={}", groupId, userId);
                return false;
            }
            
            QueryWrapper<Group_member> query = new QueryWrapper<>();
            query.eq("group_id", groupId)
                 .eq("user_id", userId)
                 .in("role", "CREATOR", "ADMIN");
            
            Group_member member = groupMemberMapper.selectOne(query);
            boolean isAdmin = member != null;
            
            logger.debug("群管理员权限验证: groupId={}, userId={}, isAdmin={}", groupId, userId, isAdmin);
            return isAdmin;
        } catch (Exception e) {
            logger.error("验证群管理员权限时发生错误: groupId={}, userId={}", groupId, userId, e);
            return false;
        }
    }
    
    /**
     * 验证用户是否可以管理群成员
     * 当前只有群主可以管理成员
     * @param groupId 群组ID
     * @param operatorId 操作者ID
     * @return 是否可以管理成员
     */
    public boolean canManageMembers(String groupId, int operatorId) {
        return isGroupCreator(groupId, operatorId);
    }
    
    /**
     * 验证用户是否可以修改群组信息
     * 当前只有群主可以修改群组信息
     * @param groupId 群组ID
     * @param operatorId 操作者ID
     * @return 是否可以修改群组信息
     */
    public boolean canModifyGroupInfo(String groupId, int operatorId) {
        return isGroupCreator(groupId, operatorId);
    }
    
    /**
     * 验证用户是否为群组成员
     * @param groupId 群组ID
     * @param userId 用户ID
     * @return 是否为群组成员
     */
    public boolean isGroupMember(String groupId, int userId) {
        try {
            if (groupId == null || groupId.trim().isEmpty() || userId <= 0) {
                logger.warn("群组成员验证参数无效: groupId={}, userId={}", groupId, userId);
                return false;
            }
            
            QueryWrapper<Group_member> query = new QueryWrapper<>();
            query.eq("group_id", groupId).eq("user_id", userId);
            
            boolean isMember = groupMemberMapper.exists(query);
            
            logger.debug("群组成员验证: groupId={}, userId={}, isMember={}", groupId, userId, isMember);
            return isMember;
        } catch (Exception e) {
            logger.error("验证群组成员时发生错误: groupId={}, userId={}", groupId, userId, e);
            return false;
        }
    }
    
    /**
     * 获取用户在群组中的角色
     * @param groupId 群组ID
     * @param userId 用户ID
     * @return 用户角色，如果不是成员则返回null
     */
    public String getUserRoleInGroup(String groupId, int userId) {
        try {
            if (groupId == null || groupId.trim().isEmpty() || userId <= 0) {
                return null;
            }
            
            QueryWrapper<Group_member> query = new QueryWrapper<>();
            query.eq("group_id", groupId).eq("user_id", userId);
            
            Group_member member = groupMemberMapper.selectOne(query);
            String role = member != null ? member.getRole() : null;
            
            logger.debug("获取用户群组角色: groupId={}, userId={}, role={}", groupId, userId, role);
            return role;
        } catch (Exception e) {
            logger.error("获取用户群组角色时发生错误: groupId={}, userId={}", groupId, userId, e);
            return null;
        }
    }
    
    /**
     * 验证操作目标的有效性
     * 确保不能对自己进行某些操作（如踢出自己）
     * @param operatorId 操作者ID
     * @param targetId 目标用户ID
     * @return 操作是否有效
     */
    public boolean isValidTarget(int operatorId, int targetId) {
        boolean isValid = operatorId != targetId && operatorId > 0 && targetId > 0;
        
        if (!isValid) {
            logger.warn("无效的操作目标: operatorId={}, targetId={}", operatorId, targetId);
        }
        
        return isValid;
    }
} 