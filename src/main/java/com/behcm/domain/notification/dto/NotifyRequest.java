package com.behcm.domain.notification.dto;

import lombok.Data;

@Data
public class NotifyRequest {
    private String title;
    private String body;
}