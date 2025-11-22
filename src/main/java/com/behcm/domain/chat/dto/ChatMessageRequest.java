package com.behcm.domain.chat.dto;

import com.behcm.domain.chat.entity.MessageType;
import lombok.Data;

@Data
public class ChatMessageRequest {
    private MessageType type;
    private String content;
}
