package com.behcm.global.config.toss;

import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
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
 *
 * <h2>락이 지켜야 하는 두 가지</h2>
 *
 * <p><b>1. TTL 은 발급이 걸릴 수 있는 최악의 시간보다 길어야 한다.</b> 짧으면 발급 도중 락이 풀려
 * 두 번째 발급이 나가고, 그 순간 첫 토큰이 무효가 되어 이 클래스가 막으려던 사고가 그대로 난다.
 *
 * <p><b>2. 해제는 내가 잡은 락만 지워야 한다.</b> 무조건 지우면, 내 락이 이미 만료돼 다른 요청이
 * 새로 잡은 상태에서 남의 락을 풀어 버린다. 그래서 락 값에 요청마다 다른 고유값(fence)을 넣고
 * 값이 일치할 때만 지운다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossTokenStore {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String TOKEN_KEY_PREFIX = "toss_invest:access_token:";
    private static final String LOCK_KEY_PREFIX = "toss_invest:token_lock:";

    /**
     * 락 보유자가 죽어도 영구 교착에 빠지지 않도록 하는 안전장치.
     *
     * <p>토큰 발급 1회의 최악 시간은 connect 5초 + read 5초 = <b>10초</b>다
     * ({@code spring.http.clients.*}). 그보다 짧게 잡으면 발급이 끝나기 전에 락이 풀린다.
     * HTTP 타임아웃을 바꾸면 이 값도 함께 봐야 한다.
     */
    static final Duration LOCK_TTL = Duration.ofSeconds(15);

    /**
     * 락을 못 잡은 쪽이 승자를 기다리는 시간.
     *
     * <p>{@link #LOCK_TTL} 에서 파생시킨다 — 이보다 짧으면 승자가 정상적으로 발급하는 중인데도
     * 기다리던 쪽이 먼저 실패한다. 승자가 죽은 경우는 타임아웃이 아니라 "락이 사라졌다"로 먼저
     * 판정되므로({@link #awaitToken}), 이 값까지 다 기다리는 일은 드물다.
     */
    static final long AWAIT_TIMEOUT_MS = LOCK_TTL.toMillis() + 2_000L;

    private static final long AWAIT_POLL_INTERVAL_MS = 100L;

    /** 승자가 실패해 락이 풀린 경우를 위한 재시도 횟수. 전체 대기 시간은 그래도 위 상한을 넘지 않는다. */
    private static final int MAX_ACQUIRE_ATTEMPTS = 2;

    /**
     * 값이 내 fence 와 같을 때만 지운다. GET 후 DEL 을 따로 부르면 그 사이에 락이 만료되고
     * 남이 새로 잡을 수 있어, 원자적으로 처리해야 한다.
     */
    private static final RedisScript<Long> RELEASE_IF_OWNED = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

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
        // 시도를 거듭해도 전체 대기 시간이 늘어나지 않도록 마감 시각은 한 번만 잡는다.
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;

        for (int attempt = 0; attempt < MAX_ACQUIRE_ATTEMPTS; attempt++) {
            String fence = UUID.randomUUID().toString();

            if (Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, fence, LOCK_TTL))) {
                try {
                    // 락을 기다리는 사이에 승자가 이미 채워 두었을 수 있다.
                    String cached = read(owner);
                    if (cached != null) {
                        return cached;
                    }

                    IssuedToken issued = issuer.apply(owner);
                    redisTemplate.opsForValue()
                            .set(TOKEN_KEY_PREFIX + owner.name(), issued.accessToken(), issued.ttl());
                    return issued.accessToken();
                } finally {
                    releaseIfOwned(lockKey, fence);
                }
            }

            // 다른 요청이 재발급 중이다. 같이 발급하면 서로의 토큰을 무효화하므로 결과를 기다린다.
            String token = awaitToken(owner, lockKey, deadline);
            if (token != null) {
                return token;
            }
            // 락이 사라졌는데 토큰이 없다 = 승자가 실패했다. 여기서 포기하면 승자가 죽을 때마다
            // 뒤따르던 요청이 전부 실패하므로, 이번엔 직접 잡아 본다.
            log.info("The Toss token lock holder finished without a token (owner={}); retrying ourselves", owner);
        }

        log.error("Gave up acquiring the Toss token lock (owner={})", owner);
        throw new CustomException(ErrorCode.TOSS_TOKEN_ISSUE_FAILED);
    }

    /**
     * 승자가 토큰을 채우거나 락이 사라질 때까지 기다린다.
     *
     * @return 토큰. 승자가 토큰 없이 끝나 <b>직접 발급을 시도해야 하면</b> null.
     * @throws CustomException 마감까지 아무 일도 일어나지 않았을 때
     */
    private String awaitToken(TossAccountOwner owner, String lockKey, long deadline) {
        while (System.currentTimeMillis() < deadline) {
            String token = read(owner);
            if (token != null) {
                return token;
            }

            // 승자는 토큰을 먼저 쓰고 락을 나중에 푼다. 락이 없다는 것은 승자가 끝났다는 뜻이므로,
            // 여기서 한 번 더 읽어야 "방금 썼는데 아직 못 본" 경우를 놓치지 않는다.
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
                return read(owner);
            }

            try {
                Thread.sleep(AWAIT_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CustomException(ErrorCode.TOSS_TOKEN_ISSUE_FAILED);
            }
        }

        log.error("Timed out waiting for another request to issue a Toss token (owner={})", owner);
        throw new CustomException(ErrorCode.TOSS_TOKEN_ISSUE_FAILED);
    }

    /**
     * 해제 실패는 삼킨다. 락은 TTL 로도 풀리므로 최악이 잠깐의 재발급 지연이고,
     * 여기서 예외를 내보내면 <b>토큰은 정상 발급됐는데 요청만 실패</b>한다.
     */
    private void releaseIfOwned(String lockKey, String fence) {
        try {
            redisTemplate.execute(RELEASE_IF_OWNED, List.of(lockKey), fence);
        } catch (Exception e) {
            log.warn("Failed to release the Toss token lock ({}); it expires in {}s anyway",
                    lockKey, LOCK_TTL.toSeconds(), e);
        }
    }

    private String read(TossAccountOwner owner) {
        Object value = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + owner.name());
        return value instanceof String token && !token.isBlank() ? token : null;
    }
}
