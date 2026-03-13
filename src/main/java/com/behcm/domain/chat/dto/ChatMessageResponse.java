package com.behcm.domain.chat.dto;

import com.behcm.domain.chat.entity.ChatMessage;
import com.behcm.domain.chat.entity.MessageType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatMessageResponse {
    private Long id;
    private MessageType type;
    private String content;
    private String sender;
    private String imageUrl;
    private LocalDateTime timestamp;
    private Integer unreadCount;

    public static ChatMessageResponse from(ChatMessage chatMessage) {
        return ChatMessageResponse.builder()
                .id(chatMessage.getId())
                .type(chatMessage.getMessageType())
                .content(chatMessage.getContent())
                .sender(chatMessage.getSender().getNickname())
                .imageUrl(chatMessage.getImageUrl())
                .timestamp(chatMessage.getTimestamp())
                .build();
    }

    public static ChatMessageResponse from(ChatMessage chatMessage, int unreadCount) {
        return ChatMessageResponse.builder()
                .id(chatMessage.getId())
                .type(chatMessage.getMessageType())
                .content(chatMessage.getContent())
                .sender(chatMessage.getSender().getNickname())
                .imageUrl(chatMessage.getImageUrl())
                .timestamp(chatMessage.getTimestamp())
                .unreadCount(unreadCount)
                .build();
    }
}
