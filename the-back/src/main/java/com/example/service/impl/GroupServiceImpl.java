package com.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.entity.dto.Group;
import com.example.entity.dto.Group_member;
import com.example.mapper.GroupMapper;
import com.example.mapper.Group_memberMapper;
import com.example.service.GroupService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

@Service
public class GroupServiceImpl implements GroupService {

    @Resource
    GroupMapper groupMapper;
    @Resource
    Group_memberMapper groupMemberMapper;

    // 定义日期格式，匹配 DTO 中的 String joined_at
    // 推荐使用 ISO 8601 格式: "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" 或根据需要调整
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


    @Override
    @Transactional // 保证创建群组和添加创建者这两个操作是原子性的
    public Group createGroup(String groupName, String creatorId) {
        if (groupName == null || groupName.trim().isEmpty()) {
            throw new IllegalArgumentException("群聊名称不能为空");
        }
        if (creatorId == null || creatorId.trim().isEmpty()) {
            throw new IllegalArgumentException("创建者ID不能为空");
        }

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
        creatorMember.setGroup_id(newGroup.getGroupId());
        creatorMember.setUser_id(creatorId);
        creatorMember.setJoined_at(DATE_FORMAT.format(new Date())); // 格式化加入时间
        creatorMember.setRole("CREATOR"); // 或者 "ADMIN", "OWNER" 等

        int memberInserted = groupMemberMapper.insert(creatorMember);
        if (memberInserted <= 0) {
            // 如果添加成员失败，需要回滚群组创建操作 (通过 @Transactional 实现)
            throw new RuntimeException("将创建者添加到群成员失败");
        }

        // 返回创建成功的群组信息
        return newGroup;
    }

    @Override
    @Transactional // 保证查询和插入是原子性的
    public Group_member joinGroup(String groupId, String userId) {
        if (groupId == null || groupId.trim().isEmpty()) {
            throw new IllegalArgumentException("群聊ID不能为空");
        }
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        // 1. 检查群组是否存在
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new RuntimeException("加入失败：群聊不存在 (ID: " + groupId + ")");
        }

        // 2. 检查用户是否已在群组中 (使用 QueryWrapper)
        QueryWrapper<Group_member> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("group_id", groupId).eq("user_id", userId).last("LIMIT 1");
        boolean alreadyMember = groupMemberMapper.exists(queryWrapper);

        if (alreadyMember) {
            throw new RuntimeException("用户已在该群聊中");
        }

        // 3. 添加新成员
        Group_member newMember = new Group_member();
        newMember.setGroup_id(groupId);
        newMember.setUser_id(userId);
        newMember.setJoined_at(DATE_FORMAT.format(new Date())); // 格式化加入时间
        newMember.setRole("MEMBER"); // 默认角色为普通成员

        int inserted = groupMemberMapper.insert(newMember);
        if (inserted <= 0) {
            throw new RuntimeException("加入群聊失败");
        }

        // TODO: (可选) 通过 WebSocket 通知群内其他成员有新用户加入

        return newMember;
    }
}
