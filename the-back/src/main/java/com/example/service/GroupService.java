package com.example.service;

import com.example.entity.dto.Group;
import com.example.entity.dto.Group_member;

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
}
