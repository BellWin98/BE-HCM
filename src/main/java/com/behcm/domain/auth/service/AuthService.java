package com.behcm.domain.auth.service;

import com.behcm.domain.auth.dto.LoginRequest;
import com.behcm.domain.auth.dto.RegisterRequest;
import com.behcm.domain.member.dto.MemberResponse;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import com.behcm.global.security.TokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
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
    private final TokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;

    public MemberResponse register(RegisterRequest request, HttpServletResponse response) {

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
        String accessToken = tokenProvider.createAccessToken(member.getEmail(), member.getRole());
        String refreshToken = tokenProvider.createRefreshToken(member.getEmail());
        String refreshTokenId = refreshTokenService.saveRefreshToken(savedMember.getEmail(), refreshToken);
        addAccessTokenCookie(response, accessToken);
        addRefreshTokenIdCookie(response, refreshTokenId);
        log.info("회원가입 성공: {}", member.getNickname());

        return MemberResponse.from(savedMember);
    }

    public MemberResponse login(LoginRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        Member member = (Member) authentication.getPrincipal();
        String accessToken = tokenProvider.createAccessToken(member.getEmail(), member.getRole());
        String refreshToken = tokenProvider.createRefreshToken(member.getEmail());
        String refreshTokenId = refreshTokenService.saveRefreshToken(member.getEmail(), refreshToken);
        addAccessTokenCookie(response, accessToken);
        addRefreshTokenIdCookie(response, refreshTokenId);
        log.info("로그인 성공: {}", member.getNickname());

        return MemberResponse.from(member);
    }

    /**
     * 토큰 갱신 (RTR 적용)
     */
    public void refreshToken(String refreshTokenId, HttpServletResponse response) {
        // 블랙리스트 확인
        if (refreshTokenService.isBlacklisted(refreshTokenId)) {
            log.warn("블랙리스트된 Refresh Token 사용 시도: tokenId={}", refreshTokenId);
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        String tokenData = refreshTokenService.getRefreshToken(refreshTokenId);
        if (tokenData == null) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        // tokenData 파싱: "email:refreshToken"
        String[] parts = tokenData.split(":", 2);
        if (parts.length != 2) {
            throw new CustomException(ErrorCode.INVALID_TOKEN_FORMAT);
        }
        String email = parts[0];
        String refreshToken = parts[1];
        if (!tokenProvider.validateToken(refreshToken)) {
            refreshTokenService.deleteRefreshToken(refreshTokenId);
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        String tokenType = tokenProvider.getTokenType(refreshToken);
        if (!"refresh".equals(tokenType)) {
            throw new RuntimeException("Refresh Token이 아닙니다");
        }

        // RTR: 기존 Refresh Token 무효화 (블랙리스트 추가)
        refreshTokenService.invalidateRefreshToken(refreshTokenId, tokenProvider.getRefreshTokenExpiration());
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        String newAccessToken = tokenProvider.createAccessToken(member.getEmail(), member.getRole());
        String newRefreshToken = tokenProvider.createRefreshToken(member.getEmail());
        String newRefreshTokenId = refreshTokenService.saveRefreshToken(member.getEmail(), newRefreshToken);
        addAccessTokenCookie(response, newAccessToken);
        addRefreshTokenIdCookie(response, newRefreshTokenId);

        log.info("토큰 갱신 완료: {}", member.getNickname());
    }

    public void logout(String refreshTokenId, HttpServletResponse response) {
        if (refreshTokenId != null) {
            refreshTokenService.deleteRefreshToken(refreshTokenId);
            log.info("로그아웃: refreshTokenId={}", refreshTokenId);
        }

        // 쿠키 삭제
        clearAuthCookies(response);
    }

    @Transactional(readOnly = true)
    public MemberResponse getCurrentUser(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        return MemberResponse.from(member);
    }

    /**
     * Access Token 쿠키 추가
     */
    private void addAccessTokenCookie(HttpServletResponse response, String accessToken) {
        ResponseCookie cookie = ResponseCookie
                .from("accessToken", accessToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(tokenProvider.getAccessTokenExpiration() / 1000)
                .sameSite("Strict")
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }

    /**
     * Refresh Token ID 쿠키 추가
     */
    private void addRefreshTokenIdCookie(HttpServletResponse response, String refreshTokenId) {
        ResponseCookie cookie = ResponseCookie
                .from("refreshTokenId", refreshTokenId)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(tokenProvider.getRefreshTokenExpiration() / 1000)
                .sameSite("Strict")
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }

    /**
     * 인증 쿠키 삭제
     */
    private void clearAuthCookies(HttpServletResponse response) {
        ResponseCookie accessTokenCookie = ResponseCookie
                .from("accessToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        ResponseCookie refreshTokenCookie = ResponseCookie
                .from("refreshTokenId", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        response.addHeader("Set-Cookie", accessTokenCookie.toString());
        response.addHeader("Set-Cookie", refreshTokenCookie.toString());
    }
}
