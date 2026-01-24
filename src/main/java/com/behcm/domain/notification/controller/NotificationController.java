package com.behcm.domain.notification.controller;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.notification.dto.FcmTokenRequest;
import com.behcm.domain.notification.dto.NotifyChatRequest;
import com.behcm.domain.notification.dto.NotifyWorkoutRequest;
import com.behcm.domain.notification.service.NotificationService;
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

    private final NotificationService notificationService;

    @PostMapping("/fcm/token")
    @Operation(summary = "FCM 토큰 등록", description = "사용자의 FCM 토큰을 등록하거나 갱신합니다.")
    public ResponseEntity<ApiResponse<String>> registerFcmToken(
            @Valid @RequestBody FcmTokenRequest request,
            @AuthenticationPrincipal Member member
    ) {
        notificationService.registerFcmToken(member, request);
        return ResponseEntity.ok(ApiResponse.success(null, "FCM 토큰이 등록되었습니다."));
    }

    @PostMapping("/rooms/{roomId}/workout")
    @Operation(summary = "운동 인증 알림", description = "운동 인증 시 같은 방의 다른 멤버들에게 푸시 알림을 전송합니다.")
    public ResponseEntity<ApiResponse<String>> notifyWorkout(
            @PathVariable Long roomId,
            @Valid @RequestBody NotifyWorkoutRequest request,
            @AuthenticationPrincipal Member member
    ) {
        notificationService.notifyWorkout(member, roomId, request);
        return ResponseEntity.ok(ApiResponse.success(null, "운동 인증 알림이 전송되었습니다."));
    }

    @PostMapping("/rooms/{roomId}/chat")
    @Operation(summary = "채팅 메시지 알림", description = "채팅 메시지 발송 시 같은 방의 다른 멤버들에게 푸시 알림을 전송합니다.")
    public ResponseEntity<ApiResponse<String>> notifyChat(
            @PathVariable Long roomId,
            @Valid @RequestBody NotifyChatRequest request,
            @AuthenticationPrincipal Member member
    ) {
        notificationService.notifyChat(member, roomId, request);
        return ResponseEntity.ok(ApiResponse.success(null, "채팅 알림이 전송되었습니다."));
    }
}
