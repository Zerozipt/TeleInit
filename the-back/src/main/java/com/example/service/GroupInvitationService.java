package com.example.service;

import com.example.entity.vo.response.GroupInvitationResponse;
import java.util.List;

public interface GroupInvitationService {
    /**
     * 发送群组邀请
     * @param groupId 群组ID
     * @param inviterId 邀请者ID
     * @param inviteeId 被邀请者ID
     * @return 是否成功
     */
    boolean inviteUserToGroup(String groupId, int inviterId, int inviteeId);
    
    /**
     * 处理邀请（接受/拒绝）
     * @param invitationId 邀请ID
     * @param userId 用户ID（确认是被邀请者）
     * @param action 操作（accept/reject）
     * @return 是否成功
     */
    boolean respondToInvitation(long invitationId, int userId, String action);
    
    /**
     * 获取用户收到的邀请
     * @param userId 用户ID
     * @return 邀请列表
     */
    List<GroupInvitationResponse> getUserReceivedInvitations(int userId);
    
    /**
     * 获取用户发出的邀请
     * @param userId 用户ID
     * @return 邀请列表
     */
    List<GroupInvitationResponse> getUserSentInvitations(int userId);
} 