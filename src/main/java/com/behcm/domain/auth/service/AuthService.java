package com.behcm.domain.auth.service;

import com.behcm.domain.auth.dto.AuthResponse;
import com.behcm.domain.auth.dto.LoginRequest;
import com.behcm.domain.auth.dto.RegisterRequest;
import com.behcm.domain.member.dto.MemberResponse;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.global.common.TokenResponse;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import com.behcm.global.logging.LogMask;
import com.behcm.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;

    public AuthResponse register(RegisterRequest request) {

        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (memberRepository.existsByNickname(request.getNickname())) {
            throw new CustomException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }
        Member member = Member.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .profileUrl("")
                .build();
        Member savedMember = memberRepository.save(member);
        TokenResponse tokenResponse = tokenProvider.generateTokensByEmail(savedMember.getEmail());

        refreshTokenService.storeRefreshToken(savedMember.getEmail(), tokenResponse.getRefreshToken());
        log.info("Member registered (memberId={})", savedMember.getId());

        return new AuthResponse(
                tokenResponse.getAccessToken(),
                tokenResponse.getRefreshToken(),
                MemberResponse.from(savedMember)
        );
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String accessToken = tokenProvider.generateAccessToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(authentication);
        Member member = memberRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        refreshTokenService.storeRefreshToken(member.getEmail(), refreshToken);
        // 로그인은 보안 이벤트다. 실패는 GlobalExceptionHandler(BadCredentials)가 WARN 으로 남긴다.
        log.info("Member logged in (memberId={})", member.getId());

        return new AuthResponse(accessToken, refreshToken, MemberResponse.from(member));
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken)) {
            // 서명/만료 실패 — 사유는 JwtTokenProvider 가 남긴다.
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        
        String email = tokenProvider.getEmailFromJwt(refreshToken);
        
        if (!refreshTokenService.isRefreshTokenValid(email, refreshToken)) {
            // 토큰 자체는 유효한데 Redis 의 것과 다르다. "저장된 게 없음"이 급증하면 Redis 유실(재시작·eviction)로
            // 전원이 로그아웃된 것이고, "불일치"는 이미 교체된 옛 토큰의 재사용이다. 둘은 대응이 다르다.
            String stored = refreshTokenService.getRefreshToken(email);
            log.warn("Refresh token rejected ({}, email={})",
                    stored == null ? "no stored token" : "mismatch with stored token", LogMask.email(email));
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        
        TokenResponse tokenResponse = tokenProvider.generateTokensByEmail(email);
        
        refreshTokenService.storeRefreshToken(email, tokenResponse.getRefreshToken());
        
        return new AuthResponse(tokenResponse.getAccessToken(), tokenResponse.getRefreshToken(), MemberResponse.from(member));
    }
}
