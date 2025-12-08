package com.behcm.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FcmTokenRequest {

    @NotBlank(message = "토큰은 필수입니다")
    private String token;

    public FcmTokenRequest(String token) {
        this.token = token;
    }
}