package com.behcm.domain.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(redisTemplate);
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpiration", 604_800_000L);
    }

    @Test
    @DisplayName("storeRefreshToken은 refresh_token:: 접두사가 붙은 키로 만료시간과 함께 저장한다")
    void storeRefreshToken_savesWithPrefixedKeyAndExpiration() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        refreshTokenService.storeRefreshToken("user@test.com", "refresh-token-value");

        verify(valueOperations).set(
                eq("refresh_token::user@test.com"),
                eq("refresh-token-value"),
                eq(604_800_000L),
                eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    @DisplayName("isRefreshTokenValid는 저장된 토큰과 일치할 때만 true를 반환한다")
    void isRefreshTokenValid_matchesStoredToken() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("refresh_token::user@test.com")).willReturn("stored-token");

        assertThat(refreshTokenService.isRefreshTokenValid("user@test.com", "stored-token")).isTrue();
        assertThat(refreshTokenService.isRefreshTokenValid("user@test.com", "different-token")).isFalse();
    }

    @Test
    @DisplayName("isRefreshTokenValid는 저장된 토큰이 없으면 false를 반환한다 (NPE 없이)")
    void isRefreshTokenValid_noStoredToken_returnsFalse() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("refresh_token::missing@test.com")).willReturn(null);

        assertThat(refreshTokenService.isRefreshTokenValid("missing@test.com", "any-token")).isFalse();
    }

    @Test
    @DisplayName("deleteRefreshToken은 접두사가 붙은 키를 삭제한다")
    void deleteRefreshToken_deletesPrefixedKey() {
        refreshTokenService.deleteRefreshToken("user@test.com");

        verify(redisTemplate).delete("refresh_token::user@test.com");
    }
}
