package com.behcm.domain.chat.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatImageUploadResponse {

    private String imageUrl;

    public static ChatImageUploadResponse of(String imageUrl) {
        return ChatImageUploadResponse.builder()
                .imageUrl(imageUrl)
                .build();
    }
}
