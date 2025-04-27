package com.example.utils;

import com.example.entity.dto.Group_message;
import com.example.entity.vo.response.ChatMessage;
import com.example.entity.vo.response.FriendsResponse;
import com.example.entity.dto.Account;
import com.example.entity.dto.PrivateChatMessage;
import com.example.entity.dto.Friends;
import com.example.service.AccountService;
import jakarta.annotation.Resource;


public class ConvertUtils {

    @Resource
    private AccountService accountService;

    public static Group_message convertToGroupMessage(ChatMessage message){
        Group_message group_message = new Group_message();
        System.out.println("转换群组消息: " + message);
        group_message.setGroupId(message.getGroupId());
        group_message.setContent(message.getContent());
        group_message.setSenderId(message.getSenderId());
        group_message.setCreateAt(message.getTimestamp());
        group_message.setContentType((short)1);
        group_message.setFileUrl(message.getFileUrl());
        return group_message;
    }

    public static PrivateChatMessage convertToPrivateChatMessage(ChatMessage message){
        PrivateChatMessage privateChatMessage = new PrivateChatMessage();
        privateChatMessage.setSenderId(message.getSenderId());
        privateChatMessage.setReceiverId(message.getReceiverId());
        privateChatMessage.setContent(message.getContent());
        privateChatMessage.setCreatedAt(message.getTimestamp());
        privateChatMessage.setFileUrl(message.getFileUrl());
        return privateChatMessage;
    }

    public static ChatMessage convertToChatMessage(PrivateChatMessage privateChatMessage){
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSenderId(privateChatMessage.getSenderId());
        chatMessage.setReceiverId(privateChatMessage.getReceiverId());
        chatMessage.setContent(privateChatMessage.getContent());
        chatMessage.setTimestamp(privateChatMessage.getCreatedAt());
        chatMessage.setFileUrl(privateChatMessage.getFileUrl());
        return chatMessage;
    }

    public static ChatMessage convertToChatMessage(Group_message group_message){    
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setGroupId(group_message.getGroupId());
        chatMessage.setContent(group_message.getContent());
        chatMessage.setSenderId(group_message.getSenderId());
        chatMessage.setTimestamp(group_message.getCreateAt());
        chatMessage.setFileUrl(group_message.getFileUrl());
        return chatMessage;
    }

    public static FriendsResponse convertToFriendsResponse(Friends friends, AccountService accountService){
        FriendsResponse friendsResponse = new FriendsResponse();
        friendsResponse.setFirstUserId(String.valueOf(friends.getTheFirstUserId()));
        Account account = accountService.getAccountById(friends.getTheFirstUserId());
        friendsResponse.setFirstUsername(account.getUsername());
        friendsResponse.setSecondUserId(String.valueOf(friends.getTheSecondUserId()));
        Account account2 = accountService.getAccountById(friends.getTheSecondUserId());
        friendsResponse.setSecondUsername(account2.getUsername());
        friendsResponse.setCreated_at(friends.getCreatedAt());
        friendsResponse.setStatus(FriendsResponse.Status.valueOf(friends.getStatus().name()));
        return friendsResponse;
    }

}
