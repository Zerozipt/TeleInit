package com.example.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.dto.Group_member;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface Group_memberMapper extends BaseMapper<Group_member> {

    /**
     * 根据用户ID查询其所在的群组列表，并包含群组名称
     * @param userId 用户ID
     * @return 包含群组名称的 Group_member 列表
     */
    @Select("SELECT gm.user_id, gm.group_id, gm.joined_at, gm.role, g.name as groupname " +
            "FROM group_members gm " +
            "JOIN `Group` g ON gm.group_id = g.group_id " +
            "WHERE gm.user_id = #{userId}")
    List<Group_member> findUserGroupsWithNames(@Param("userId") int userId);

}

