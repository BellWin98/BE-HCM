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

        return new AuthResponse(accessToken, refreshToken, MemberResponse.from(member));
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        
        String email = tokenProvider.getEmailFromJwt(refreshToken);
        
        if (!refreshTokenService.isRefreshTokenValid(email, refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        
        TokenResponse tokenResponse = tokenProvider.generateTokensByEmail(email);
        
        refreshTokenService.storeRefreshToken(email, tokenResponse.getRefreshToken());
        
        return new AuthResponse(tokenResponse.getAccessToken(), tokenResponse.getRefreshToken(), MemberResponse.from(member));
    }

    @Transactional(readOnly = true)
    public MemberResponse getCurrentUser(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        return MemberResponse.from(member);
    }
}
