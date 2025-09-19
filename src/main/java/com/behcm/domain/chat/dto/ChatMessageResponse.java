package com.behcm.domain.chat.dto;

import com.behcm.domain.chat.entity.ChatMessage;
import com.behcm.domain.chat.entity.MessageType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Builder
public class ChatMessageResponse {
    private Long id;
    private MessageType type;
    private String content;
    private String sender;
    private String imageUrl;
    private Set<String> readBy;
    private LocalDateTime timestamp;

    public static ChatMessageResponse from(ChatMessage chatMessage) {
        return ChatMessageResponse.builder()
                .id(chatMessage.getId())
                .type(chatMessage.getMessageType())
                .content(chatMessage.getContent())
                .sender(chatMessage.getSender().getNickname())
                .imageUrl(chatMessage.getImageUrl())
                .readBy(chatMessage.getReadBy())
                .timestamp(chatMessage.getTimestamp())
                .build();
    }
}
