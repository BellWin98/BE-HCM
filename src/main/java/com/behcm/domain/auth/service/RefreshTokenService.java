package com.behcm.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token::";

    public void storeRefreshToken(String email, String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + email;
        redisTemplate.opsForValue().set(key, refreshToken, refreshTokenExpiration, TimeUnit.MILLISECONDS);
    }

    public String getRefreshToken(String email) {
        String key = REFRESH_TOKEN_PREFIX + email;
        return (String) redisTemplate.opsForValue().get(key);
    }

    public void deleteRefreshToken(String email) {
        String key = REFRESH_TOKEN_PREFIX + email;
        redisTemplate.delete(key);
    }

    public boolean isRefreshTokenValid(String email, String refreshToken) {
        String storedToken = getRefreshToken(email);
        return storedToken != null && storedToken.equals(refreshToken);
    }
}
