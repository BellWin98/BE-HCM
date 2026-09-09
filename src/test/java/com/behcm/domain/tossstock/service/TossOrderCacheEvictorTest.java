package com.behcm.domain.tossstock.service;

import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 주문 후 캐시 무효화.
 *
 * <p>{@code @SpringBootTest} 를 쓰는 이유는 이 동작이 <b>프록시가 걸려야만</b> 일어나기 때문이다 —
 * 순수 단위 테스트로는 {@code @CacheEvict} 가 붙었는지조차 확인할 수 없고, 같은 빈 안에서 호출해
 * 프록시를 못 타는 고전적인 실수도 잡히지 않는다.
 *
 * <p>등록되지 않은 캐시 이름으로 evict 하면 {@code SimpleCacheManager} 가 예외를 던져
 * <b>주문은 성공했는데 응답은 실패</b>가 된다. 그 사고도 여기서 걸린다.
 */
@SpringBootTest
class TossOrderCacheEvictorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private TossInvestClient tossInvestClient;

    @Autowired
    private TossHoldingsReader holdingsReader;

    @Autowired
    private TossOrderHistoryReader orderHistoryReader;

    @Autowired
    private TossOrderCacheEvictor cacheEvictor;

    @Autowired
    private CacheManager cacheManager;

    private JsonNode holdings(String label) {
        return objectMapper.readTree("{\"items\":[],\"label\":\"" + label + "\"}");
    }

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    @DisplayName("evict 하기 전에는 보유주식을 캐시에서 준다")
    void cachesHoldingsUntilEvicted() {
        given(tossInvestClient.get(eq(TossAccountOwner.ME), any(), any(), any())).willReturn(holdings("first"));

        holdingsReader.read(TossAccountOwner.ME, 1L);
        holdingsReader.read(TossAccountOwner.ME, 1L);

        verify(tossInvestClient, times(1)).get(eq(TossAccountOwner.ME), any(), any(), any());
    }

    @Test
    @DisplayName("주문 후 evict 하면 보유주식을 다시 조회한다")
    void evictsHoldingsCache() {
        // 방금 거래한 사람에게 30초 묵은 잔고를 보여 주면 안 된다.
        given(tossInvestClient.get(eq(TossAccountOwner.ME), any(), any(), any())).willReturn(holdings("first"));
        holdingsReader.read(TossAccountOwner.ME, 1L);

        cacheEvictor.evictAccountCaches(TossAccountOwner.ME);

        given(tossInvestClient.get(eq(TossAccountOwner.ME), any(), any(), any())).willReturn(holdings("second"));
        JsonNode reread = holdingsReader.read(TossAccountOwner.ME, 1L);

        assertThat(reread.path("label").asString("")).isEqualTo("second");
        verify(tossInvestClient, times(2)).get(eq(TossAccountOwner.ME), any(), any(), any());
    }

    @Test
    @DisplayName("한 소유자를 evict 해도 다른 소유자의 캐시는 남는다")
    void evictsOnlyTheGivenOwner() {
        given(tossInvestClient.get(any(), any(), any(), any())).willReturn(holdings("first"));
        holdingsReader.read(TossAccountOwner.ME, 1L);
        holdingsReader.read(TossAccountOwner.MOM, 2L);

        cacheEvictor.evictAccountCaches(TossAccountOwner.ME);

        holdingsReader.read(TossAccountOwner.MOM, 2L);

        // 엄마 계좌는 캐시에 그대로 있어야 한다 — 내가 주문했다고 남의 조회가 다시 나갈 이유가 없다.
        verify(tossInvestClient, times(1)).get(eq(TossAccountOwner.MOM), any(), any(), any());
    }

    @Test
    @DisplayName("evict 대상 캐시가 모두 CacheConfig 에 등록되어 있다")
    void evictedCachesAreRegistered() {
        // 등록되지 않은 이름으로 evict 하면 주문은 성공했는데 응답만 실패하는 최악의 조합이 나온다.
        assertThat(cacheManager.getCacheNames()).contains("tossHoldings", "tossOrderHistory");
    }

    @Test
    @DisplayName("주문 후 evict 하면 주문내역 스냅샷도 비워져 전체 기간을 다시 읽는다")
    void evictsOrderHistorySnapshot() {
        // 증분 동기화는 캐시된 스냅샷을 기준으로 재조회 창을 잡는다. evict 로 스냅샷이 사라지면
        // 창이 아니라 전체를 다시 읽어야 한다 — 그렇지 않으면 오래된 체결이 영영 비어 있게 된다.
        given(tossInvestClient.get(eq(TossAccountOwner.ME), eq("/api/v1/orders"), any(), any()))
                .willReturn(objectMapper.readTree("{\"orders\":[],\"nextCursor\":null,\"hasNext\":false}"));

        orderHistoryReader.readAll(TossAccountOwner.ME, 1L);
        cacheEvictor.evictAccountCaches(TossAccountOwner.ME);
        orderHistoryReader.readAll(TossAccountOwner.ME, 1L);

        ArgumentCaptor<Map<String, String>> params = ArgumentCaptor.forClass(Map.class);
        verify(tossInvestClient, times(2))
                .get(eq(TossAccountOwner.ME), eq("/api/v1/orders"), params.capture(), any());
        assertThat(params.getAllValues()).allSatisfy(p -> assertThat(p).doesNotContainKey("from"));
    }
}
