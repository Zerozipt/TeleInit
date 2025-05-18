package com.example.service;

import com.example.entity.dto.Group_member;
import com.example.entity.vo.response.GroupDetailResponse;
import java.util.List;

public interface GroupCacheService {
    /**
     * 获取用户加入的群组列表，优先从缓存
     */
    List<Group_member> getUserGroups(int userId);

    /**
     * 获取群组详情，优先从缓存
     */
    GroupDetailResponse getGroupDetail(String groupId);

    /**
     * 使指定用户的群组列表缓存失效
     */
    void invalidateUserGroups(int userId);

    /**
     * 使指定群组的详情缓存失效
     */
    void invalidateGroupDetail(String groupId);
} 