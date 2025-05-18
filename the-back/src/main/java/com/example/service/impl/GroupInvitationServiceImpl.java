package com.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.entity.dto.Account;
import com.example.entity.dto.Group;
import com.example.entity.dto.GroupInvitation;
import com.example.entity.vo.response.GroupInvitationResponse;
import com.example.mapper.AccountMapper;
import com.example.mapper.GroupInvitationMapper;
import com.example.mapper.GroupMapper;
import com.example.service.GroupInvitationService;
import com.example.service.GroupService;
import com.example.utils.Const;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class GroupInvitationServiceImpl implements GroupInvitationService {

    private static final Logger logger = LoggerFactory.getLogger(GroupInvitationServiceImpl.class);

    @Resource
    private GroupInvitationMapper groupInvitationMapper;

    @Resource
    private GroupMapper groupMapper;

    @Resource
    private AccountMapper accountMapper;

    @Resource
    private GroupService groupService;

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public boolean inviteUserToGroup(String groupId, int inviterId, int inviteeId) {
        // 验证群组是否存在
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            logger.warn("尝试邀请加入不存在的群组: {}", groupId);
            return false;
        }

        // 验证邀请者是否为群组成员
        if (!groupService.isGroupMember(groupId, inviterId)) {
            logger.warn("非群组成员尝试发送邀请: 用户ID={}, 群组ID={}", inviterId, groupId);
            return false;
        }

        // 验证被邀请者是否已是群组成员
        if (groupService.isGroupMember(groupId, inviteeId)) {
            logger.warn("尝试邀请已经是群组成员的用户: 用户ID={}, 群组ID={}", inviteeId, groupId);
            return false;
        }

        // 检查是否有未处理的邀请
        QueryWrapper<GroupInvitation> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("group_id", groupId)
                .eq("invitee_id", inviteeId)
                .eq("status", "pending");
        
        if (groupInvitationMapper.exists(queryWrapper)) {
            logger.info("已存在未处理的邀请: 群组ID={}, 被邀请者ID={}", groupId, inviteeId);
            return false;
        }

        // 创建邀请记录
        GroupInvitation invitation = new GroupInvitation();
        invitation.setGroupId(groupId);
        invitation.setInviterId(inviterId);
        invitation.setInviteeId(inviteeId);
        invitation.setStatus("pending");
        invitation.setCreatedAt(new Date());

        int result = groupInvitationMapper.insert(invitation);
        if (result > 0) {
            // 通过WebSocket通知被邀请者
            notifyUserAboutInvitation(inviteeId, invitation.getId());
            return true;
        }
        
        return false;
    }

    @Override
    @Transactional
    public boolean respondToInvitation(long invitationId, int userId, String action) {
        // 查询邀请记录
        GroupInvitation invitation = groupInvitationMapper.selectById(invitationId);
        if (invitation == null) {
            logger.warn("尝试响应不存在的邀请: {}", invitationId);
            return false;
        }

        // 验证被邀请者身份
        if (invitation.getInviteeId() != userId) {
            logger.warn("用户 {} 尝试响应不属于他的邀请 {}", userId, invitationId);
            return false;
        }

        // 验证邀请状态
        if (!"pending".equals(invitation.getStatus())) {
            logger.warn("尝试响应已处理的邀请: {}, 当前状态: {}", invitationId, invitation.getStatus());
            return false;
        }

        // 更新邀请状态
        invitation.setStatus(action);
        invitation.setUpdatedAt(new Date());
        
        int updateResult = groupInvitationMapper.updateById(invitation);
        if (updateResult <= 0) {
            logger.error("更新邀请状态失败: {}", invitationId);
            return false;
        }

        // 如果接受邀请，将用户添加到群组
        if ("accepted".equals(action)) {
            try {
                groupService.joinGroupById(invitation.getGroupId(), userId);
                // 通知邀请者
                notifyInviterAboutResponse(invitation, "accepted");
            } catch (Exception e) {
                logger.error("加入群组失败: {}", e.getMessage(), e);
                return false;
            }
        } else if ("rejected".equals(action)) {
            // 通知邀请者
            notifyInviterAboutResponse(invitation, "rejected");
        }
        
        return true;
    }

    @Override
    public List<GroupInvitationResponse> getUserReceivedInvitations(int userId) {
        QueryWrapper<GroupInvitation> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("invitee_id", userId)
                .orderByDesc("created_at");
                
        List<GroupInvitation> invitations = groupInvitationMapper.selectList(queryWrapper);
        return convertToResponseList(invitations);
    }

    @Override
    public List<GroupInvitationResponse> getUserSentInvitations(int userId) {
        QueryWrapper<GroupInvitation> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("inviter_id", userId)
                .orderByDesc("created_at");
                
        List<GroupInvitation> invitations = groupInvitationMapper.selectList(queryWrapper);
        return convertToResponseList(invitations);
    }

    // 转换为响应VO
    private List<GroupInvitationResponse> convertToResponseList(List<GroupInvitation> invitations) {
        List<GroupInvitationResponse> responses = new ArrayList<>();
        
        for (GroupInvitation invitation : invitations) {
            GroupInvitationResponse response = new GroupInvitationResponse();
            response.setId(invitation.getId());
            response.setGroupId(invitation.getGroupId());
            response.setInviterId(invitation.getInviterId());
            response.setInviteeId(invitation.getInviteeId());
            response.setStatus(invitation.getStatus());
            response.setCreatedAt(invitation.getCreatedAt());
            
            // 获取群组名称
            Group group = groupMapper.selectById(invitation.getGroupId());
            if (group != null) {
                response.setGroupName(group.getName());
            }
            
            // 获取邀请者姓名
            Account inviter = accountMapper.selectById(invitation.getInviterId());
            if (inviter != null) {
                response.setInviterName(inviter.getUsername());
            }
            
            // 获取被邀请者姓名
            Account invitee = accountMapper.selectById(invitation.getInviteeId());
            if (invitee != null) {
                response.setInviteeName(invitee.getUsername());
            }
            
            responses.add(response);
        }
        
        return responses;
    }
    
    // 通过WebSocket通知被邀请者
    private void notifyUserAboutInvitation(int userId, Long invitationId) {
        try {
            GroupInvitationResponse invitation = convertToResponseList(
                    List.of(groupInvitationMapper.selectById(invitationId))).get(0);
            
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/notifications",
                    invitation
            );
            
            logger.info("已向用户 {} 发送群组邀请通知", userId);
        } catch (Exception e) {
            logger.error("发送群组邀请通知失败: {}", e.getMessage(), e);
        }
    }
    
    // 通知邀请者关于邀请的响应
    private void notifyInviterAboutResponse(GroupInvitation invitation, String action) {
        try {
            GroupInvitationResponse response = convertToResponseList(List.of(invitation)).get(0);
            
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(invitation.getInviterId()),
                    "/queue/notifications",
                    response
            );
            
            logger.info("已向用户 {} 发送邀请响应通知, 状态: {}", invitation.getInviterId(), action);
        } catch (Exception e) {
            logger.error("发送邀请响应通知失败: {}", e.getMessage(), e);
        }
    }
} 