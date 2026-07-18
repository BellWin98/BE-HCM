package com.behcm.domain.auth.service;

import com.behcm.domain.auth.dto.AuthResponse;
import com.behcm.domain.auth.dto.LoginRequest;
import com.behcm.domain.auth.dto.RegisterRequest;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.global.common.TokenResponse;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import com.behcm.global.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    private Member member(String email, String nickname) {
        Member m = Member.builder()
                .email(email)
                .password("encoded-pw")
                .nickname(nickname)
                .role(MemberRole.USER)
                .build();
        setId(m, 1L);
        return m;
    }

    private void setId(Object entity, long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private RegisterRequest registerRequest(String email, String nickname) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword("Password1!");
        request.setNickname(nickname);
        return request;
    }

    @Test
    @DisplayName("register는 중복 이메일이면 EMAIL_ALREADY_EXISTS 예외를 던지고 저장하지 않는다")
    void register_duplicateEmail_throwsEmailAlreadyExists() {
        given(memberRepository.existsByEmail("dup@test.com")).willReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest("dup@test.com", "nick")))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_ALREADY_EXISTS);

        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("register는 중복 닉네임이면 NICKNAME_ALREADY_EXISTS 예외를 던지고 저장하지 않는다")
    void register_duplicateNickname_throwsNicknameAlreadyExists() {
        given(memberRepository.existsByEmail("new@test.com")).willReturn(false);
        given(memberRepository.existsByNickname("dupNick")).willReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest("new@test.com", "dupNick")))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NICKNAME_ALREADY_EXISTS);

        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("register는 신규 회원을 저장하고 토큰을 발급하며 refresh token을 저장한다")
    void register_newMember_savesAndIssuesTokens() {
        given(memberRepository.existsByEmail("new@test.com")).willReturn(false);
        given(memberRepository.existsByNickname("nick")).willReturn(false);
        given(passwordEncoder.encode("Password1!")).willReturn("encoded-pw");
        Member saved = member("new@test.com", "nick");
        given(memberRepository.save(any(Member.class))).willReturn(saved);
        given(tokenProvider.generateTokensByEmail("new@test.com"))
                .willReturn(new TokenResponse("access-token", "refresh-token"));

        AuthResponse response = authService.register(registerRequest("new@test.com", "nick"));

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getMember().getEmail()).isEqualTo("new@test.com");
        verify(refreshTokenService).storeRefreshToken("new@test.com", "refresh-token");
    }

    @Test
    @DisplayName("login은 인증 성공 시 토큰을 발급하고 refresh token을 저장한다")
    void login_authenticationSucceeds_issuesTokens() {
        Member existing = member("user@test.com", "user");
        Authentication authentication = new UsernamePasswordAuthenticationToken(existing, "", existing.getAuthorities());
        given(authenticationManager.authenticate(any())).willReturn(authentication);
        given(tokenProvider.generateAccessToken(authentication)).willReturn("access-token");
        given(tokenProvider.generateRefreshToken(authentication)).willReturn("refresh-token");
        given(memberRepository.findByEmail("user@test.com")).willReturn(Optional.of(existing));

        try {
            LoginRequest request = new LoginRequest();
            request.setEmail("user@test.com");
            request.setPassword("Password1!");

            AuthResponse response = authService.login(request);

            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
            verify(refreshTokenService).storeRefreshToken("user@test.com", "refresh-token");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("login은 인증은 성공했지만 회원을 찾을 수 없으면 MEMBER_NOT_FOUND 예외를 던진다")
    void login_memberNotFoundAfterAuthentication_throwsMemberNotFound() {
        Member existing = member("ghost@test.com", "ghost");
        Authentication authentication = new UsernamePasswordAuthenticationToken(existing, "", existing.getAuthorities());
        given(authenticationManager.authenticate(any())).willReturn(authentication);
        given(memberRepository.findByEmail("ghost@test.com")).willReturn(Optional.empty());

        try {
            LoginRequest request = new LoginRequest();
            request.setEmail("ghost@test.com");
            request.setPassword("Password1!");

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("refreshToken은 토큰 자체가 유효하지 않으면 INVALID_TOKEN 예외를 던진다")
    void refreshToken_invalidToken_throwsInvalidToken() {
        given(tokenProvider.validateToken("bad-token")).willReturn(false);

        assertThatThrownBy(() -> authService.refreshToken("bad-token"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("refreshToken은 토큰은 유효하지만 저장된 refresh token과 다르면 INVALID_TOKEN 예외를 던진다")
    void refreshToken_notMatchingStoredToken_throwsInvalidToken() {
        given(tokenProvider.validateToken("stale-token")).willReturn(true);
        given(tokenProvider.getEmailFromJwt("stale-token")).willReturn("user@test.com");
        given(refreshTokenService.isRefreshTokenValid("user@test.com", "stale-token")).willReturn(false);

        assertThatThrownBy(() -> authService.refreshToken("stale-token"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("refreshToken은 유효한 토큰이면 새 토큰 쌍을 발급하고 저장소를 갱신한다")
    void refreshToken_validToken_issuesNewTokenPair() {
        Member existing = member("user@test.com", "user");
        given(tokenProvider.validateToken("old-refresh")).willReturn(true);
        given(tokenProvider.getEmailFromJwt("old-refresh")).willReturn("user@test.com");
        given(refreshTokenService.isRefreshTokenValid("user@test.com", "old-refresh")).willReturn(true);
        given(memberRepository.findByEmail("user@test.com")).willReturn(Optional.of(existing));
        given(tokenProvider.generateTokensByEmail("user@test.com"))
                .willReturn(new TokenResponse("new-access", "new-refresh"));

        AuthResponse response = authService.refreshToken("old-refresh");

        assertThat(response.getAccessToken()).isEqualTo("new-access");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenService).storeRefreshToken("user@test.com", "new-refresh");
    }
}
