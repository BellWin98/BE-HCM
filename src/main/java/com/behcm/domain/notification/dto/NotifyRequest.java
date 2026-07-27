package com.behcm.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NotifyRequest {

    @NotBlank(message = "제목은 필수입니다")
    @Size(max = 100, message = "제목은 100자 이하여야 합니다")
    private String title;

    @NotBlank(message = "내용은 필수입니다")
    @Size(max = 500, message = "내용은 500자 이하여야 합니다")
    private String body;

    @NotBlank(message = "알림 종류는 필수입니다")
    @Size(max = 50, message = "알림 종류는 50자 이하여야 합니다")
    private String type;
}
