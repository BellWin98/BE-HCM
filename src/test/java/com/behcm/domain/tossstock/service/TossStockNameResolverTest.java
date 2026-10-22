package com.behcm.domain.tossstock.service;

import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 심볼 → 종목명.
 *
 * <p>검색용으로 이미 메모리에 들고 있는 유니버스가 이름을 알고 있으면 외부 호출을 하지 않는다 —
 * 미체결 목록을 열 때마다, 주문내역 전체 동기화 때마다 나가던 {@code /stocks} 호출이 대부분 사라진다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TossStockNameResolverTest {

    private static final TossAccountOwner OWNER = TossAccountOwner.ME;
    private static final String STOCKS_PATH = "/api/v1/stocks";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TossInvestClient tossInvestClient;

    private TossStockUniverse universe;
    private TossStockNameResolver resolver;

    @BeforeEach
    void setUp() {
        universe = new TossStockUniverse();
        resolver = new TossStockNameResolver(tossInvestClient, universe);
    }

    private JsonNode json(String text) {
        return objectMapper.readTree(text);
    }

    @Test
    @DisplayName("유니버스가 아는 종목은 외부 호출 없이 이름을 채운다")
    void resolvesFromUniverseWithoutCallingToss() {
        universe.replace(List.of(TossListedStock.of("005930", "삼성전자", "KOSPI", "STOCK", true)));

        Map<String, String> names = resolver.resolve(OWNER, List.of("005930"));

        assertThat(names).containsEntry("005930", "삼성전자");
        verify(tossInvestClient, never()).get(any(), eq(STOCKS_PATH), any());
    }

    @Test
    @DisplayName("유니버스에 없는 종목만 토스에 묻는다")
    void asksTossOnlyForSymbolsMissingFromUniverse() {
        // 상장폐지 종목처럼 오늘 유니버스(ACTIVE)에 없는 과거 체결 종목이 섞일 수 있다.
        universe.replace(List.of(TossListedStock.of("005930", "삼성전자", "KOSPI", "STOCK", true)));
        given(tossInvestClient.get(eq(OWNER), eq(STOCKS_PATH), any())).willReturn(json("""
                [{"symbol": "999999", "name": "옛종목"}]
                """));

        Map<String, String> names = resolver.resolve(OWNER, List.of("005930", "999999"));

        assertThat(names).containsEntry("005930", "삼성전자").containsEntry("999999", "옛종목");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> params = ArgumentCaptor.forClass(Map.class);
        verify(tossInvestClient).get(eq(OWNER), eq(STOCKS_PATH), params.capture());
        assertThat(params.getValue()).containsEntry("symbols", "999999");
    }

    @Test
    @DisplayName("유니버스가 아직 비어 있으면 전부 토스에 묻는다")
    void fallsBackToTossWhenUniverseIsEmpty() {
        given(tossInvestClient.get(eq(OWNER), eq(STOCKS_PATH), any())).willReturn(json("""
                [{"symbol": "005930", "name": "삼성전자"}]
                """));

        assertThat(resolver.resolve(OWNER, List.of("005930"))).containsEntry("005930", "삼성전자");
    }

    @Test
    @DisplayName("토스 조회가 실패해도 유니버스에서 찾은 이름은 돌려준다")
    void keepsUniverseNamesWhenTossFails() {
        universe.replace(List.of(TossListedStock.of("005930", "삼성전자", "KOSPI", "STOCK", true)));
        given(tossInvestClient.get(eq(OWNER), eq(STOCKS_PATH), any())).willThrow(new RuntimeException("boom"));

        Map<String, String> names = resolver.resolve(OWNER, List.of("005930", "999999"));

        assertThat(names).containsEntry("005930", "삼성전자").doesNotContainKey("999999");
    }
}
