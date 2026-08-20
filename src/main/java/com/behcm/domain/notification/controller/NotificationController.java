package com.behcm.domain.notification.controller;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.notification.dto.FcmTokenRequest;
import com.behcm.domain.notification.service.NotificationFacade;
import com.behcm.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification", description = "푸시 알림 API")
public class NotificationController {

    private final NotificationFacade notificationFacade;

    @PostMapping("/fcm/token")
    @Operation(summary = "FCM 토큰 등록", description = "사용자의 FCM 토큰을 등록하거나 갱신합니다.")
    public ResponseEntity<ApiResponse<String>> registerFcmToken(
            @Valid @RequestBody FcmTokenRequest request,
            @AuthenticationPrincipal Member member
    ) {
        notificationFacade.registerFcmToken(member, request.getToken());
        return ResponseEntity.ok(ApiResponse.success(null, "FCM 토큰이 등록되었습니다."));
    }
}
