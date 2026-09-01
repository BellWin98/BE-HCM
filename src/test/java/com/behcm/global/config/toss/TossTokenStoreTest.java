package com.behcm.global.config.toss;

import com.behcm.global.config.toss.TossTokenStore.IssuedToken;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TossTokenStoreTest {

    private static final String TOKEN_KEY = "toss_invest:access_token:ME";
    private static final String LOCK_KEY = "toss_invest:token_lock:ME";

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private TossTokenStore tokenStore;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("캐시에 토큰이 있으면 재발급하지 않는다")
    void getAccessToken_withCachedToken_doesNotIssue() {
        given(valueOperations.get(TOKEN_KEY)).willReturn("cached-token");
        AtomicInteger issueCount = new AtomicInteger();

        String token = tokenStore.getAccessToken(TossAccountOwner.ME, owner -> {
            issueCount.incrementAndGet();
            return new IssuedToken("new-token", Duration.ofMinutes(10));
        });

        assertThat(token).isEqualTo("cached-token");
        assertThat(issueCount).hasValue(0);
        verify(valueOperations, never()).setIfAbsent(eq(LOCK_KEY), any(), any(Duration.class));
    }

    @Test
    @DisplayName("캐시가 비어 있으면 락을 잡고 발급한 뒤 캐시에 저장한다")
    void getAccessToken_withEmptyCache_issuesUnderLockAndCaches() {
        given(valueOperations.get(TOKEN_KEY)).willReturn(null);
        given(valueOperations.setIfAbsent(eq(LOCK_KEY), any(), any(Duration.class))).willReturn(true);

        String token = tokenStore.getAccessToken(TossAccountOwner.ME,
                owner -> new IssuedToken("new-token", Duration.ofMinutes(10)));

        assertThat(token).isEqualTo("new-token");
        verify(valueOperations).set(eq(TOKEN_KEY), eq("new-token"), eq(Duration.ofMinutes(10)));
        // 락은 반드시 해제되어야 한다 — 남으면 TTL 만료까지 모든 재발급이 막힌다.
        verify(redisTemplate).delete(LOCK_KEY);
    }

    @Test
    @DisplayName("토큰은 소유자별로 다른 키에 저장된다")
    void getAccessToken_forDifferentOwners_usesSeparateKeys() {
        given(valueOperations.get("toss_invest:access_token:MOM")).willReturn(null);
        given(valueOperations.setIfAbsent(eq("toss_invest:token_lock:MOM"), any(), any(Duration.class)))
                .willReturn(true);

        tokenStore.getAccessToken(TossAccountOwner.MOM,
                owner -> new IssuedToken("mom-token", Duration.ofMinutes(10)));

        verify(valueOperations).set(eq("toss_invest:access_token:MOM"), eq("mom-token"), any(Duration.class));
    }

    @Test
    @DisplayName("락을 못 잡으면 직접 발급하지 않고 다른 요청이 저장한 토큰을 기다린다")
    void getAccessToken_whenLockNotAcquired_waitsForTheWinnerInsteadOfIssuing() {
        // 토스는 client 당 토큰이 1개라 동시 발급하면 서로를 무효화한다.
        given(valueOperations.get(TOKEN_KEY)).willReturn(null, null, "winner-token");
        given(valueOperations.setIfAbsent(eq(LOCK_KEY), any(), any(Duration.class))).willReturn(false);
        AtomicInteger issueCount = new AtomicInteger();

        String token = tokenStore.getAccessToken(TossAccountOwner.ME, owner -> {
            issueCount.incrementAndGet();
            return new IssuedToken("loser-token", Duration.ofMinutes(10));
        });

        assertThat(token).isEqualTo("winner-token");
        assertThat(issueCount).hasValue(0);
    }

    @Test
    @DisplayName("발급이 실패해도 락을 해제한다")
    void getAccessToken_whenIssuerThrows_releasesLock() {
        given(valueOperations.get(TOKEN_KEY)).willReturn(null);
        given(valueOperations.setIfAbsent(eq(LOCK_KEY), any(), any(Duration.class))).willReturn(true);

        assertThatThrownBy(() -> tokenStore.getAccessToken(TossAccountOwner.ME, owner -> {
            throw new CustomException(ErrorCode.TOSS_TOKEN_ISSUE_FAILED);
        })).isInstanceOf(CustomException.class);

        verify(redisTemplate).delete(LOCK_KEY);
    }

    @Test
    @DisplayName("evict는 해당 소유자의 토큰만 지운다")
    void evict_removesOnlyThatOwnersToken() {
        tokenStore.evict(TossAccountOwner.ME);

        verify(redisTemplate).delete(TOKEN_KEY);
        verify(redisTemplate, never()).delete("toss_invest:access_token:MOM");
    }

    @Test
    @DisplayName("빈 문자열이 캐시돼 있으면 캐시 미스로 다룬다")
    void getAccessToken_withBlankCachedValue_treatsAsMiss() {
        given(valueOperations.get(TOKEN_KEY)).willReturn("   ");
        given(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class))).willReturn(true);

        String token = tokenStore.getAccessToken(TossAccountOwner.ME,
                owner -> new IssuedToken("new-token", Duration.ofMinutes(10)));

        assertThat(token).isEqualTo("new-token");
    }
}
