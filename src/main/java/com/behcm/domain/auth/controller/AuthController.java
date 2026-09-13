package com.behcm.domain.auth.controller;

import com.behcm.domain.auth.dto.*;
import com.behcm.domain.auth.service.AuthService;
import com.behcm.domain.auth.service.EmailVerificationService;
import com.behcm.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/check-email")
    public ResponseEntity<ApiResponse<Void>> checkEmailDuplicate(@Valid @RequestBody EmailRequest request) {
        emailVerificationService.checkEmailDuplicate(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/send-verification")
    public ResponseEntity<ApiResponse<Void>> sendVerificationEmail(@Valid @RequestBody EmailRequest request) {
        // sendVerificationEmail 은 @Async 라 안에서 던진 EMAIL_ALREADY_EXISTS 가 응답에 실리지 않는다
        // (200 이 먼저 나가고 예외는 비동기 스레드에 남는다). 중복 검사는 여기서 동기로 먼저 한다.
        emailVerificationService.checkEmailDuplicate(request.getEmail());
        emailVerificationService.sendVerificationEmail(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmailCode(@Valid @RequestBody EmailVerificationConfirmRequest request) {
        emailVerificationService.verifyEmailCode(request.getEmail(), request.getCode());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
