package com.behcm.global.config.toss;

import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Function;

/**
 * 토스증권 액세스 토큰 캐시.
 *
 * <p>토스는 <b>client 당 유효한 토큰이 1개</b>이고 재발급하면 이전 토큰이 즉시 무효화된다.
 * 따라서 두 요청이 동시에 재발급하면 서로의 토큰을 죽여 401 루프에 빠진다.
 * 한국투자증권 클라이언트에는 이 보호가 없으므로(락 없이 재발급) 그대로 복사해서는 안 된다.
 *
 * <p>재발급 경로는 Redis 분산 락으로 직렬화하고, 락을 못 잡은 쪽은 승자가 캐시에 써 줄 때까지 기다린다.
 * 캐시 키는 소유자별로 분리한다 — 사람마다 client 가 다르므로 토큰도 다르다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossTokenStore {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String TOKEN_KEY_PREFIX = "toss_invest:access_token:";
    private static final String LOCK_KEY_PREFIX = "toss_invest:token_lock:";

    /** 락 보유자가 죽어도 영구 교착에 빠지지 않도록 하는 안전장치. */
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    /** 락을 못 잡은 쪽이 승자를 기다리는 시간. */
    private static final long AWAIT_TIMEOUT_MS = 3_000L;
    private static final long AWAIT_POLL_INTERVAL_MS = 100L;

    /**
     * 발급된 토큰과 캐시에 담을 TTL.
     */
    public record IssuedToken(String accessToken, Duration ttl) { }

    public String getAccessToken(TossAccountOwner owner, Function<TossAccountOwner, IssuedToken> issuer) {
        String cached = read(owner);
        if (cached != null) {
            return cached;
        }
        return issueUnderLock(owner, issuer);
    }

    public void evict(TossAccountOwner owner) {
        redisTemplate.delete(TOKEN_KEY_PREFIX + owner.name());
    }

    private String issueUnderLock(TossAccountOwner owner, Function<TossAccountOwner, IssuedToken> issuer) {
        String lockKey = LOCK_KEY_PREFIX + owner.name();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);

        if (!Boolean.TRUE.equals(acquired)) {
            // 다른 요청이 재발급 중이다. 같이 발급하면 서로의 토큰을 무효화하므로 결과를 기다린다.
            String token = awaitToken(owner);
            if (token != null) {
                return token;
            }
            log.error("Timed out waiting for another thread to issue a Toss token (owner={})", owner);
            throw new CustomException(ErrorCode.TOSS_TOKEN_ISSUE_FAILED);
        }

        try {
            // 락을 기다리는 사이에 승자가 이미 채워 두었을 수 있다.
            String cached = read(owner);
            if (cached != null) {
                return cached;
            }

            IssuedToken issued = issuer.apply(owner);
            redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + owner.name(), issued.accessToken(), issued.ttl());
            return issued.accessToken();
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private String awaitToken(TossAccountOwner owner) {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(AWAIT_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            String token = read(owner);
            if (token != null) {
                return token;
            }
        }
        return null;
    }

    private String read(TossAccountOwner owner) {
        Object value = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + owner.name());
        return value instanceof String token && !token.isBlank() ? token : null;
    }
}
