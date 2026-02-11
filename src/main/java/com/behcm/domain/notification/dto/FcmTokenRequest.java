package com.behcm.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
public class FcmTokenRequest {
    @NotBlank(message = "토큰은 필수입니다")
    private String token;
}