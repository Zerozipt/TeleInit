package com.example.service.impl;

import com.example.entity.dto.Group_member;
import com.example.entity.dto.Group;
import com.example.entity.dto.Account;
import com.example.mapper.Group_memberMapper;
import com.example.mapper.GroupMapper;
import com.example.mapper.AccountMapper;
import com.example.service.GroupCacheService;
import com.example.service.RedisService;
import com.example.utils.RedisKeys;
import com.example.entity.vo.response.GroupDetailResponse;
import com.example.entity.vo.response.GroupMemberResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.ArrayList;

@Service
public class GroupCacheServiceImpl implements GroupCacheService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    @Autowired
    private RedisService redisService;

    @Autowired
    private Group_memberMapper groupMemberMapper;

    @Autowired
    private GroupMapper groupMapper;

    @Autowired
    private AccountMapper accountMapper;

    @Override
    public List<Group_member> getUserGroups(int userId) {
        String key = RedisKeys.USER_GROUPS + userId;
        String json = redisService.get(key);
        if (json != null) {
            try {
                return JSON.parseArray(json, Group_member.class);
            } catch (Exception e) {
                // 解析错误，忽略并回落数据库
            }
        }
        // 从数据库获取
        QueryWrapper<Group_member> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        List<Group_member> members = groupMemberMapper.selectList(qw);
        // 缓存结果
        try {
            redisService.set(key, JSON.toJSONString(members), CACHE_TTL);
        } catch (Exception e) {
            // 日志或忽略
        }
        return members;
    }

    @Override
    public GroupDetailResponse getGroupDetail(String groupId) {
        String key = RedisKeys.GROUP_DETAIL + groupId;
        String json = redisService.get(key);
        if (json != null) {
            try {
                return JSON.parseObject(json, GroupDetailResponse.class);
            } catch (Exception e) {
                // 解析错误，忽略并回落数据库
            }
        }
        // 构建响应对象
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            return null;
        }
        List<Group_member> members = groupMemberMapper.selectList(
                new QueryWrapper<Group_member>().eq("group_id", groupId)
        );
        GroupDetailResponse response = new GroupDetailResponse();
        response.setGroupId(group.getGroupId());
        response.setName(group.getName());
        response.setCreatorId(group.getCreatorId());
        response.setMemberCount(members.size());
        Account creator = accountMapper.selectById(group.getCreatorId());
        if (creator != null) {
            response.setCreatorName(creator.getUsername());
            Group_member creatorMember = members.stream()
                    .filter(m -> m.getUserId() == group.getCreatorId())
                    .findFirst().orElse(null);
            if (creatorMember != null && creatorMember.getJoinedAt() != null) {
                response.setCreateAt(creatorMember.getJoinedAt());
            } else {
                response.setCreateAt(group.getCreate_at());
            }
        } else {
            response.setCreateAt(group.getCreate_at());
        }
        List<GroupMemberResponse> memberResponses = new ArrayList<>();
        for (Group_member member : members) {
            GroupMemberResponse mr = new GroupMemberResponse();
            mr.setUserId(member.getUserId());
            mr.setRole(member.getRole());
            mr.setJoinedAt(member.getJoinedAt());
            Account acc = accountMapper.selectById(member.getUserId());
            if (acc != null) {
                mr.setUsername(acc.getUsername());
                mr.setAvatar(null);
            }
            memberResponses.add(mr);
        }
        response.setMembers(memberResponses);
        // 缓存结果
        try {
            redisService.set(key, JSON.toJSONString(response), CACHE_TTL);
        } catch (Exception e) {
            // 日志或忽略
        }
        return response;
    }

    @Override
    public void invalidateUserGroups(int userId) {
        String key = RedisKeys.USER_GROUPS + userId;
        redisService.delete(key);
    }

    @Override
    public void invalidateGroupDetail(String groupId) {
        String key = RedisKeys.GROUP_DETAIL + groupId;
        redisService.delete(key);
    }
} 