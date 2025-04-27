package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.dto.PrivateChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface Private_messagesMapper extends BaseMapper<PrivateChatMessage> {
    
}
