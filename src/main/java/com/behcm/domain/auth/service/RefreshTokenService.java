package com.behcm.domain.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token::";
    private static final String BLACKLIST_PREFIX = "blacklist::";

    /**
     * Refresh Token을 Redis에 저장하고 고유 ID 반환
     */
    public String saveRefreshToken(String email, String refreshToken) {
        String tokenId = UUID.randomUUID().toString();
        String key = REFRESH_TOKEN_PREFIX + tokenId;
        String value = email + ":" + refreshToken;
        redisTemplate.opsForValue().set(key, value, refreshTokenExpiration, TimeUnit.MILLISECONDS);
        log.debug("Refresh Token 저장 완료: tokenId={}, userId={}", tokenId, email);

        return tokenId;
    }

    public String getRefreshToken(String refreshTokenId) {
        String key = REFRESH_TOKEN_PREFIX + refreshTokenId;
        return (String) redisTemplate.opsForValue().get(key);
    }

    public void deleteRefreshToken(String refreshTokenId) {
        String key = REFRESH_TOKEN_PREFIX + refreshTokenId;
        Boolean deleted = redisTemplate.delete(key);
        log.debug("Refresh Token 삭제: refreshTokenId={}, deleted={}", refreshTokenId, deleted);
    }

    /**
     * 토큰이 블랙리스트에 있는지 확인
     */
    public boolean isBlacklisted(String refreshTokenId) {
        String blacklistKey = BLACKLIST_PREFIX + refreshTokenId;
        Boolean exists = redisTemplate.hasKey(blacklistKey);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * RTR: 기존 토큰 무효화 및 블랙리스트 추가
     */
    public void invalidateRefreshToken(String refreshTokenId, long refreshTokenExpiration) {
        // 기존 RefreshToken 삭제
        deleteRefreshToken(refreshTokenId);

        // 블랙리스트에 추가 (재사용 방지)
        String blacklistKey = BLACKLIST_PREFIX + refreshTokenId;
        redisTemplate.opsForValue().set(blacklistKey, "true", refreshTokenExpiration, TimeUnit.MILLISECONDS);
        log.debug("Refresh Token 블랙리스트 추가: refreshTokenId={}", refreshTokenId);
    }
}
