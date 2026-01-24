package com.behcm.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class NotifyChatRequest {

    @NotBlank(message = "메시지는 필수입니다")
    private String message;

    public NotifyChatRequest(String message) {
        this.message = message;
    }
}