package com.behcm.domain.tossstock.service;

import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 보유주식은 캐시하지 않는다.
 *
 * <p>자산 화면은 폴링이 아니라 사용자가 새로고침을 눌러야 다시 받아온다 — 즉 캐시가 히트하는
 * 상황이 곧 "사용자가 지금 값을 보려고 누른 순간"이라, TTL 캐시는 눌러도 숫자가 그대로인
 * 고장처럼 보인다. 한투와 달리 응답 하나에 종목별 손익까지 들어 있어 종목 수만큼의 추가 호출도
 * 없으므로 아낄 호출 자체가 적다.
 *
 * <p>{@code @SpringBootTest} 로 확인하는 이유는 캐시 여부가 <b>프록시가 걸리느냐</b>의 문제라
 * 순수 단위 테스트로는 {@code @Cacheable} 이 다시 붙어도 잡히지 않기 때문이다.
 */
class TossHoldingsReaderTest extends IntegrationTestSupport {

    @Autowired
    private TossHoldingsReader holdingsReader;

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
    @DisplayName("조회할 때마다 토스에 다시 물어본다")
    void readsThroughOnEveryCall() {
        given(tossInvestClient.get(eq(TossAccountOwner.ME), any(), any(), any())).willReturn(holdings("first"));
        holdingsReader.read(TossAccountOwner.ME, 1L);

        given(tossInvestClient.get(eq(TossAccountOwner.ME), any(), any(), any())).willReturn(holdings("second"));
        JsonNode reread = holdingsReader.read(TossAccountOwner.ME, 1L);

        // 새로고침을 누른 사람에게는 방금 움직인 주가가 보여야 한다.
        assertThat(reread.path("label").asString("")).isEqualTo("second");
        verify(tossInvestClient, times(2)).get(eq(TossAccountOwner.ME), any(), any(), any());
    }

    @Test
    @DisplayName("보유주식 캐시는 아예 등록되어 있지 않다")
    void holdingsCacheIsNotRegistered() {
        // 이름이 남아 있으면 @Cacheable 을 다시 붙이는 순간 조용히 되살아난다.
        assertThat(cacheManager.getCacheNames()).doesNotContain("tossHoldings");
        // 주문내역 스냅샷은 성격이 다르다 — 페이징 비용이 크고 워터마크로 증분 갱신되므로 유지한다.
        assertThat(cacheManager.getCacheNames()).contains("tossOrderHistory");
    }
}
