package com.example.utils;

import com.example.entity.dto.Group_message;
import com.example.entity.vo.response.ChatMessage;
import com.example.entity.vo.response.FriendsResponse;
import com.example.entity.dto.Account;


public class ConvertUtils {
    public static Group_message convertToGroupMessage(ChatMessage message){
        Group_message group_message = new Group_message();
        group_message.setGroupId(message.getGroupId());
        group_message.setContent(message.getContent());
        group_message.setSender(message.getSender());
        group_message.setCreate_at(message.getTimestamp());
        group_message.setContent_type((short)1);
        group_message.setFile_url(null);
        return group_message;
    }

    public static FriendsResponse convertToFriendsResponse(Account account){
        FriendsResponse friendsResponse = new FriendsResponse();
        friendsResponse.setUserId(account.getId().toString());
        friendsResponse.setUsername(account.getUsername());
        friendsResponse.setCreated_at(account.getRegister_time());
        return friendsResponse;
    }
}
