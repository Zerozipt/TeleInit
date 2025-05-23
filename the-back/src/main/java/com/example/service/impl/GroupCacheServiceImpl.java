package com.example.service.impl;

import com.example.entity.dto.Group_member;
import com.example.entity.dto.Group;
import com.example.entity.dto.Account;
import com.example.mapper.Group_memberMapper;
import com.example.mapper.GroupMapper;
import com.example.mapper.AccountMapper;
import com.example.service.GroupCacheService;
import com.example.service.RedisService;
import com.example.service.SmartCacheService;
import com.example.utils.RedisKeys;
import com.example.entity.vo.response.GroupDetailResponse;
import com.example.entity.vo.response.GroupMemberResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.ArrayList;

@Service
public class GroupCacheServiceImpl implements GroupCacheService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final Logger logger = LoggerFactory.getLogger(GroupCacheServiceImpl.class);

    @Autowired
    private RedisService redisService;

    @Autowired
    private Group_memberMapper groupMemberMapper;

    @Autowired
    private GroupMapper groupMapper;

    @Autowired
    private AccountMapper accountMapper;
    
    // P2优化：集成智能缓存服务
    @Autowired
    private SmartCacheService smartCacheService;

    @Override
    public List<Group_member> getUserGroups(int userId) {
        String key = RedisKeys.USER_GROUPS + userId;
        
        // P2优化：对于复杂泛型，使用传统方式结合智能失效
        try {
            String cached = redisService.get(key);
            if (cached != null && !"NULL".equals(cached)) {
                return JSON.parseArray(cached, Group_member.class);
            }
            
            // 查询数据库
            QueryWrapper<Group_member> qw = new QueryWrapper<>();
            qw.eq("user_id", userId);
            List<Group_member> members = groupMemberMapper.selectList(qw);
            
            // 缓存结果
            if (members != null) {
                redisService.set(key, JSON.toJSONString(members), CACHE_TTL);
                smartCacheService.addToFilter(key);
            }
            
            return members;
        } catch (Exception e) {
            // 降级到直接查询数据库
            QueryWrapper<Group_member> qw = new QueryWrapper<>();
            qw.eq("user_id", userId);
            return groupMemberMapper.selectList(qw);
        }
    }

    @Override
    public GroupDetailResponse getGroupDetail(String groupId) {
        // 🔧 HOTFIX: 临时降级到直接数据库查询，确保数据一致性
        // 解决缓存不一致导致的"群组不存在"问题
        try {
            return buildGroupDetailResponse(groupId);
        } catch (Exception e) {
            logger.error("获取群组详情失败: groupId={}", groupId, e);
            return null;
        }
    }
    
    /**
     * 构建群组详情响应对象
     */
    private GroupDetailResponse buildGroupDetailResponse(String groupId) {
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
        
        return response;
    }

    @Override
    public void invalidateUserGroups(int userId) {
        String key = RedisKeys.USER_GROUPS + userId;
        
        // P2优化：智能失效并预热
        smartCacheService.smartInvalidateAndWarmup(key, () -> {
            QueryWrapper<Group_member> qw = new QueryWrapper<>();
            qw.eq("user_id", userId);
            return groupMemberMapper.selectList(qw);
        });
    }

    @Override
    public void invalidateGroupDetail(String groupId) {
        String key = RedisKeys.GROUP_DETAIL + groupId;
        
        // P2优化：智能失效并预热
        smartCacheService.smartInvalidateAndWarmup(key, () -> {
            return buildGroupDetailResponse(groupId);
        });
    }
} 