package com.behcm.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ReadStatusMessage {

    private String type;
    private List<UpdatedMessage> updatedMessages;

    public static ReadStatusMessage of(List<UpdatedMessage> updatedMessages) {
        return new ReadStatusMessage("READ_STATUS", updatedMessages);
    }

    @Getter
    @AllArgsConstructor
    public static class UpdatedMessage {
        private Long messageId;
        private Integer unreadCount;
    }
}

