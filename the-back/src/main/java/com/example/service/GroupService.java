package com.example.service;

import com.example.entity.dto.Group;
import com.example.entity.dto.Group_member;
import com.example.entity.vo.response.GroupDetailResponse;
import java.util.List;

public interface GroupService {

    /**
     * 创建群聊
     * @param groupName 群聊名称
     * @param creatorId 创建者用户ID
     * @return 创建成功的群组信息
     * @throws RuntimeException 如果创建失败或发生错误
     */
    Group createGroup(String groupName, int creatorId);

    /**
     * 加入群聊
     * @param groupId 要加入的群聊ID
     * @param userId  要加入的用户ID
     * @return 加入成功后的成员信息
     * @throws RuntimeException 如果群聊不存在、用户已在群聊中或发生其他错误
     */
    Group_member joinGroup(String groupId, int userId);

    /**
     * 通过群组 ID 加入群聊
     * @param groupId 群组 ID
     * @param userId 用户 ID
     * @return 新加入的群成员信息
     */
    Group_member joinGroupById(String groupId, int userId);

    /**
     * 根据群聊名称获取群聊列表
     * @param groupName 群聊名称
     * @return 群聊列表
     */
    List<Group> getGroupByName(String groupName);

    /**
     * 根据用户ID获取用户加入的群聊列表
     * @param userId 用户ID
     * @return 用户加入的群聊列表
     */
    List<Group_member> getGroupMembers(int userId);
    
    /**
     * 获取群组详细信息，包括成员列表
     * @param groupId 群组ID
     * @return 群组详情VO
     */
    GroupDetailResponse getGroupDetail(String groupId);
    
    /**
     * 检查用户是否为群组成员
     * @param groupId 群组ID
     * @param userId 用户ID
     * @return 是否为成员
     */
    boolean isGroupMember(String groupId, int userId);

    /**
     * 退出群组
     * @param groupId 群组ID
     * @param userId 用户ID
     * @return 操作成功返回true，否则返回false
     */
    boolean leaveGroup(String groupId, int userId);

    // ========== 群主管理功能 ==========
    
    /**
     * 移除群组成员（踢出成员）
     * @param groupId 群组ID
     * @param memberId 要移除的成员ID
     * @return 操作成功返回true，否则返回false
     */
    boolean removeMember(String groupId, int memberId);
    
    /**
     * 更新群组名称
     * @param groupId 群组ID
     * @param newName 新的群组名称
     * @return 操作成功返回true，否则返回false
     */
    boolean updateGroupName(String groupId, String newName);
    
    /**
     * 解散群组
     * @param groupId 群组ID
     * @return 操作成功返回true，否则返回false
     */
    boolean dissolveGroup(String groupId);
    
    /**
     * 获取群组的所有成员（用于解散群组时通知）
     * @param groupId 群组ID
     * @return 群组成员列表
     */
    List<Group_member> getAllGroupMembers(String groupId);
}
