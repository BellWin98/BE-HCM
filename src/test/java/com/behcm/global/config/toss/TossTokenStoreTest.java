package com.behcm.global.config.toss;

import com.behcm.global.config.toss.TossTokenStore.IssuedToken;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
        verifyLockReleased();
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
        // 승자가 아직 락을 들고 있다 — 기다려야 한다.
        given(redisTemplate.hasKey(LOCK_KEY)).willReturn(true);
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

        verifyLockReleased();
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

    private void verifyLockReleased() {
        // 무조건 delete 하면 안 된다 — 내 락이 만료된 뒤라면 그건 남의 락이다.
        verify(redisTemplate, never()).delete(LOCK_KEY);
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), any());
    }

    // ---------------------------------------------------------------------
    // 락 펜싱 — 만료된 락을 남이 잡은 뒤 내가 지우는 사고를 막는다
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("락에는 요청마다 다른 고유값을 넣는다")
    void getAccessToken_locksWithAUniqueFenceValue() {
        // 상수("1")를 넣으면 해제 시점에 그 락이 내 것인지 확인할 방법이 없다.
        given(valueOperations.get(TOKEN_KEY)).willReturn(null);
        given(valueOperations.setIfAbsent(eq(LOCK_KEY), any(), any(Duration.class))).willReturn(true);

        ArgumentCaptor<Object> fences = ArgumentCaptor.forClass(Object.class);

        tokenStore.getAccessToken(TossAccountOwner.ME,
                owner -> new IssuedToken("token-1", Duration.ofMinutes(10)));
        tokenStore.getAccessToken(TossAccountOwner.ME,
                owner -> new IssuedToken("token-2", Duration.ofMinutes(10)));

        verify(valueOperations, times(2))
                .setIfAbsent(eq(LOCK_KEY), fences.capture(), any(Duration.class));
        assertThat(fences.getAllValues()).doesNotContainNull().doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("락 TTL 은 토큰 발급이 걸릴 수 있는 최악의 시간보다 길다")
    void lockTtl_outlivesTheWorstCaseTokenIssueTime() {
        // connect 5초 + read 5초 = 10초 (spring.http.clients.*). TTL 이 이보다 짧으면
        // 발급 도중 락이 풀려 두 번째 발급이 나가고, 토스가 첫 토큰을 무효화해 401 루프가 된다.
        assertThat(TossTokenStore.LOCK_TTL.toMillis())
                .isGreaterThan(Duration.ofSeconds(10).toMillis());
    }

    @Test
    @DisplayName("대기 시간은 락 TTL 보다 길다")
    void awaitTimeout_outlivesTheLockTtl() {
        // 짧으면 승자가 제대로 발급하는 중인데도 기다리던 쪽이 먼저 실패한다.
        assertThat(TossTokenStore.AWAIT_TIMEOUT_MS)
                .isGreaterThanOrEqualTo(TossTokenStore.LOCK_TTL.toMillis());
    }

    @Test
    @DisplayName("승자가 실패해 락이 사라지면 직접 발급을 시도한다")
    void getAccessToken_whenWinnerFailsAndLockIsGone_issuesItself() {
        // 락은 사라졌는데 토큰이 없다 = 승자가 실패했다는 뜻이다.
        // 여기서 그대로 포기하면 승자가 죽을 때마다 뒤따르던 요청이 전부 실패한다.
        given(valueOperations.get(TOKEN_KEY)).willReturn(null);
        given(valueOperations.setIfAbsent(eq(LOCK_KEY), any(), any(Duration.class)))
                .willReturn(false, true);
        given(redisTemplate.hasKey(LOCK_KEY)).willReturn(false);
        AtomicInteger issueCount = new AtomicInteger();

        String token = tokenStore.getAccessToken(TossAccountOwner.ME, owner -> {
            issueCount.incrementAndGet();
            return new IssuedToken("recovered-token", Duration.ofMinutes(10));
        });

        assertThat(token).isEqualTo("recovered-token");
        assertThat(issueCount).hasValue(1);
    }
}
