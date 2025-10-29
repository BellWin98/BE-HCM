package com.behcm.domain.auth.controller;

import com.behcm.domain.auth.dto.EmailRequest;
import com.behcm.domain.auth.dto.EmailVerificationConfirmRequest;
import com.behcm.domain.auth.dto.LoginRequest;
import com.behcm.domain.auth.dto.RegisterRequest;
import com.behcm.domain.auth.service.AuthService;
import com.behcm.domain.auth.service.EmailVerificationService;
import com.behcm.domain.member.dto.MemberResponse;
import com.behcm.global.common.ApiResponse;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    public ResponseEntity<ApiResponse<MemberResponse>> register(@Valid @RequestBody RegisterRequest request,
                                                              HttpServletResponse httpServletResponse) {
        MemberResponse response = authService.register(request, httpServletResponse);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<MemberResponse>> login(@Valid @RequestBody LoginRequest request,
                                                           HttpServletResponse httpServletResponse) {
        MemberResponse response = authService.login(request, httpServletResponse);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refreshToken(HttpServletRequest request,
                                                          HttpServletResponse response) {
        String refreshTokenId = getRefreshTokenIdFromCookie(request);
        if (refreshTokenId == null) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }
        authService.refreshToken(refreshTokenId, response);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshTokenId = getRefreshTokenIdFromCookie(request);
        authService.logout(refreshTokenId, response);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/check-email")
    public ResponseEntity<ApiResponse<Void>> checkEmailDuplicate(@Valid @RequestBody EmailRequest request) {
        emailVerificationService.checkEmailDuplicate(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/send-verification")
    public ResponseEntity<ApiResponse<Void>> sendVerificationEmail(@Valid @RequestBody EmailRequest request) {
        emailVerificationService.sendVerificationEmail(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmailCode(@Valid @RequestBody EmailVerificationConfirmRequest request) {
        emailVerificationService.verifyEmailCode(request.getEmail(), request.getCode());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private String getRefreshTokenIdFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("refreshTokenId".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }
}
