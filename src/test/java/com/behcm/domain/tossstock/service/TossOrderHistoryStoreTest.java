package com.behcm.domain.tossstock.service;

import com.behcm.domain.tossstock.service.TossOrderHistorySnapshot.OrderFill;
import com.behcm.domain.tossstock.service.TossRealizedProfitCalculator.Fill;
import com.behcm.domain.tossstock.service.TossRealizedProfitCalculator.TradeSide;
import com.behcm.global.config.toss.TossAccountOwner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 주문내역 스냅샷 저장소.
 *
 * <p>전체 페이징은 계좌가 오래될수록 비싸지는데, 스냅샷이 메모리에만 있으면 배포·재시작 때마다
 * 그 비용을 요청한 사용자가 떠안는다. 그래서 Redis 에도 둔다 — 메모리(L1)가 비어 있으면 Redis(L2)에서
 * 되살린다. Redis 는 보조 수단이라, 고장 나도 화면은 메모리 캐시만으로 계속 돌아야 한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TossOrderHistoryStoreTest {

    private static final TossAccountOwner OWNER = TossAccountOwner.ME;
    private static final String REDIS_KEY = "toss_invest:order_history:ME";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private CacheManager cacheManager;
    private TossOrderHistoryStore store;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        cacheManager = new ConcurrentMapCacheManager(TossOrderHistoryStore.CACHE_NAME);
        store = new TossOrderHistoryStore(cacheManager, redisTemplate);
    }

    /** 재시작을 흉내 낸다 — 메모리 캐시만 새로 만들고 Redis 는 그대로다. */
    private TossOrderHistoryStore restarted() {
        return new TossOrderHistoryStore(new ConcurrentMapCacheManager(TossOrderHistoryStore.CACHE_NAME), redisTemplate);
    }

    private TossOrderHistorySnapshot snapshot() {
        // 금액 자릿수(0.50, 1.234567)와 시각의 초까지 왕복에서 살아남아야 한다 — 원가 계산에 그대로 쓰인다.
        Fill buy = new Fill("AAPL", "USD", TradeSide.BUY, LocalDateTime.parse("2026-03-02T23:31:15"),
                new BigDecimal("1.234567"), new BigDecimal("229.50"), new BigDecimal("0.50"), BigDecimal.ZERO);
        Fill sell = new Fill("005930", "KRW", TradeSide.SELL, LocalDateTime.parse("2026-03-03T09:05:00"),
                new BigDecimal("10"), new BigDecimal("720000"), new BigDecimal("108"), new BigDecimal("1440"));
        Map<String, OrderFill> byOrderId = new LinkedHashMap<>();
        byOrderId.put("o-1", new OrderFill("o-1", LocalDateTime.parse("2026-03-02T22:30:00"), buy));
        byOrderId.put("o-2", new OrderFill("o-2", LocalDateTime.parse("2026-03-03T09:00:00"), sell));
        return new TossOrderHistorySnapshot(byOrderId, Map.of("AAPL", "애플", "005930", "삼성전자"),
                LocalDateTime.parse("2026-03-03T10:00:00"));
    }

    private String savedJson() {
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(REDIS_KEY), json.capture(), any(Duration.class));
        return json.getValue();
    }

    @Test
    @DisplayName("저장한 스냅샷은 메모리에서 바로 꺼낸다")
    void loadsFromMemoryAfterSave() {
        TossOrderHistorySnapshot snapshot = snapshot();
        store.save(OWNER, snapshot);

        assertThat(store.load(OWNER)).containsSame(snapshot);
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    @DisplayName("재시작으로 메모리가 비어도 Redis 에서 같은 스냅샷을 되살린다")
    void restoresFromRedisAfterRestart() {
        TossOrderHistorySnapshot snapshot = snapshot();
        store.save(OWNER, snapshot);
        String json = savedJson();
        given(valueOperations.get(REDIS_KEY)).willReturn(json);

        Optional<TossOrderHistorySnapshot> restored = restarted().load(OWNER);

        assertThat(restored).hasValue(snapshot);
        // 레코드 equals 는 BigDecimal 의 자릿수까지 본다 — 0.50 이 0.5 로 돌아오면 여기서 깨진다.
        assertThat(restored.get().byOrderId().get("o-1").fill().commission()).isEqualTo(new BigDecimal("0.50"));
    }

    @Test
    @DisplayName("Redis 에서 되살린 스냅샷은 메모리에도 올려 다음 조회부터 Redis 를 거치지 않는다")
    void warmsMemoryAfterRedisHit() {
        store.save(OWNER, snapshot());
        String json = savedJson();
        given(valueOperations.get(REDIS_KEY)).willReturn(json);
        TossOrderHistoryStore restarted = restarted();

        restarted.load(OWNER);
        restarted.load(OWNER);

        verify(valueOperations, org.mockito.Mockito.times(1)).get(REDIS_KEY);
    }

    @Test
    @DisplayName("Redis 에는 만료 시간을 붙여 저장한다 — 어긋난 스냅샷이 영구히 남지 않게")
    void savesToRedisWithTtl() {
        store.save(OWNER, snapshot());

        verify(valueOperations).set(eq(REDIS_KEY), anyString(), eq(TossOrderHistoryStore.REDIS_TTL));
    }

    @Test
    @DisplayName("Redis 조회가 실패하면 없는 것으로 보고 예외를 내보내지 않는다")
    void treatsRedisReadFailureAsMiss() {
        given(valueOperations.get(REDIS_KEY)).willThrow(new RedisConnectionFailureException("down"));

        assertThat(restarted().load(OWNER)).isEmpty();
    }

    @Test
    @DisplayName("Redis 저장이 실패해도 메모리에는 남는다")
    void keepsMemoryCopyWhenRedisWriteFails() {
        willThrow(new RedisConnectionFailureException("down"))
                .given(valueOperations).set(anyString(), anyString(), any(Duration.class));
        TossOrderHistorySnapshot snapshot = snapshot();

        assertThatCode(() -> store.save(OWNER, snapshot)).doesNotThrowAnyException();
        assertThat(store.load(OWNER)).containsSame(snapshot);
    }

    @Test
    @DisplayName("읽을 수 없는 값이면 없는 것으로 본다 — 전체 재적재가 틀린 손익보다 낫다")
    void treatsUnreadableValueAsMiss() {
        given(valueOperations.get(REDIS_KEY)).willReturn("{not json");

        assertThat(restarted().load(OWNER)).isEmpty();
    }

    @Test
    @DisplayName("형식 버전이 다르면 없는 것으로 본다 — 필드 의미가 바뀐 옛 값을 그대로 쓰지 않는다")
    void treatsOtherFormatVersionAsMiss() {
        store.save(OWNER, snapshot());
        String otherVersion = savedJson().replace(
                "\"version\":" + TossOrderHistoryStore.FORMAT_VERSION,
                "\"version\":" + (TossOrderHistoryStore.FORMAT_VERSION + 1));
        given(valueOperations.get(REDIS_KEY)).willReturn(otherVersion);

        assertThat(restarted().load(OWNER)).isEmpty();
    }

    @Test
    @DisplayName("소유자별로 따로 저장한다")
    void keepsOwnersSeparate() {
        store.save(OWNER, snapshot());

        assertThat(store.load(TossAccountOwner.MOM)).isEmpty();
    }
}
