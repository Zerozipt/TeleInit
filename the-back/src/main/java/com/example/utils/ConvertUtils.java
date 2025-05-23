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
        // 根据消息类型确定Content_type
        Short contentType = (short)0; // 默认为0表示文本
        if (message.getMessageType() != null) {
            if (message.getMessageType().equalsIgnoreCase("FILE")) {
                contentType = (short)2; // 文件
            } else if (message.getMessageType().equalsIgnoreCase("IMAGE")) {
                contentType = (short)1; // 图片
            }
        } else if (message.getFileUrl() != null && !message.getFileUrl().isEmpty()) {
            contentType = (short)2; // 有文件URL但没有指定类型，默认为文件
        }
        group_message.setContentType(contentType);
        group_message.setFileUrl(message.getFileUrl());
        group_message.setFileName(message.getFileName());
        group_message.setFileType(message.getFileType());
        group_message.setFileSize(message.getFileSize());
        group_message.setMessageType(message.getMessageType());
        return group_message;
    }

    public static PrivateChatMessage convertToPrivateChatMessage(ChatMessage message){
        PrivateChatMessage privateChatMessage = new PrivateChatMessage();
        privateChatMessage.setId(null);
        privateChatMessage.setSenderId(message.getSenderId());
        privateChatMessage.setReceiverId(message.getReceiverId());
        privateChatMessage.setContent(message.getContent());
        privateChatMessage.setCreatedAt(message.getTimestamp());
        privateChatMessage.setFileUrl(message.getFileUrl());
        privateChatMessage.setFileName(message.getFileName());
        privateChatMessage.setFileType(message.getFileType());
        privateChatMessage.setFileSize(message.getFileSize());
        privateChatMessage.setMessageType(message.getMessageType());
        return privateChatMessage;
    }

    public static ChatMessage convertToChatMessage(PrivateChatMessage privateChatMessage){
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSenderId(privateChatMessage.getSenderId());
        chatMessage.setReceiverId(privateChatMessage.getReceiverId());
        chatMessage.setContent(privateChatMessage.getContent());
        chatMessage.setTimestamp(privateChatMessage.getCreatedAt());
        chatMessage.setFileUrl(privateChatMessage.getFileUrl());
        chatMessage.setFileName(privateChatMessage.getFileName());
        chatMessage.setFileType(privateChatMessage.getFileType());
        chatMessage.setFileSize(privateChatMessage.getFileSize());
        chatMessage.setMessageType(privateChatMessage.getMessageType());
        
        // 根据消息类型设置枚举类型
        chatMessage.setType(ChatMessage.MessageType.CHAT);
        return chatMessage;
    }

    public static ChatMessage convertToChatMessage(Group_message group_message){    
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setGroupId(group_message.getGroupId());
        chatMessage.setContent(group_message.getContent());
        chatMessage.setSenderId(group_message.getSenderId());
        chatMessage.setTimestamp(group_message.getCreateAt());
        chatMessage.setFileUrl(group_message.getFileUrl());
        chatMessage.setFileName(group_message.getFileName());
        chatMessage.setFileType(group_message.getFileType());
        chatMessage.setFileSize(group_message.getFileSize());
        
        // 根据ContentType设置messageType
        if (group_message.getContentType() != null) {
            if (group_message.getContentType() == 1) {
                chatMessage.setMessageType("IMAGE");
            } else if (group_message.getContentType() == 2) {
                chatMessage.setMessageType("FILE");
            } else {
                chatMessage.setMessageType("TEXT");
            }
        } else {
            chatMessage.setMessageType(group_message.getMessageType());
        }
        
        // 设置消息类型枚举
        chatMessage.setType(ChatMessage.MessageType.CHAT);
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
