package com.behcm.domain.tossstock.service;

import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestClient;
import com.behcm.global.config.toss.TossInvestProperties;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 유니버스 적재.
 *
 * <p>이 배치가 실패하면 검색이 통째로 죽으므로, "얼마나 곱게 실패하는가"가 정상 경로만큼 중요하다.
 * 한 마켓이 무너져도 나머지로 검색이 되어야 하고, 전부 실패해도 <b>이미 갖고 있던 인덱스</b>를
 * 잃어서는 안 된다 — 어제 목록이 없는 목록보다 낫다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TossStockUniverseLoaderTest {

    private static final String STOCKS_ALL_PATH = "/api/v1/stocks/all";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TossInvestClient tossInvestClient;

    private TossInvestProperties properties;
    private TossStockUniverse universe;
    private TossStockUniverseLoader loader;

    @BeforeEach
    void setUp() {
        properties = new TossInvestProperties();
        TossInvestProperties.AccountCredentials credentials = new TossInvestProperties.AccountCredentials();
        credentials.setOwner(TossAccountOwner.ME);
        properties.setAccounts(new java.util.ArrayList<>(List.of(credentials)));

        universe = new TossStockUniverse();
        loader = new TossStockUniverseLoader(tossInvestClient, properties, universe);
    }

    private JsonNode stocks(String json) {
        return objectMapper.readTree(json);
    }

    private void stubAllMarkets(String json) {
        given(tossInvestClient.get(eq(TossAccountOwner.ME), eq(STOCKS_ALL_PATH), any()))
                .willReturn(stocks(json));
    }

    @Test
    @DisplayName("거래 대상 5개 마켓을 ACTIVE 상태로 조회한다")
    void loadsFiveTradableMarketsWithActiveStatus() {
        stubAllMarkets("[]");

        loader.reload();

        ArgumentCaptor<Map<String, String>> params = ArgumentCaptor.captor();
        verify(tossInvestClient, times(5))
                .get(eq(TossAccountOwner.ME), eq(STOCKS_ALL_PATH), params.capture());

        assertThat(params.getAllValues()).extracting(p -> p.get("market"))
                .containsExactlyInAnyOrder("KOSPI", "KOSDAQ", "NYSE", "NASDAQ", "AMEX");
        assertThat(params.getAllValues()).allSatisfy(p -> assertThat(p).containsEntry("status", "ACTIVE"));
    }

    @Test
    @DisplayName("적재한 종목에 국가·통화·LOC 지원 여부를 붙인다")
    void attachesDerivedMarketFields() {
        // 응답에는 통화도 국가도 없다. 우리가 어떤 market 을 요청했는지가 유일한 출처다.
        given(tossInvestClient.get(eq(TossAccountOwner.ME), eq(STOCKS_ALL_PATH), any()))
                .willAnswer(invocation -> {
                    Map<String, String> params = invocation.getArgument(2);
                    if ("KOSPI".equals(params.get("market"))) {
                        return stocks("""
                                [{"symbol":"005930","name":"삼성전자","securityType":"STOCK","isCommonShare":true}]
                                """);
                    }
                    if ("NASDAQ".equals(params.get("market"))) {
                        return stocks("""
                                [{"symbol":"AAPL","name":"애플","securityType":"STOCK","isCommonShare":true}]
                                """);
                    }
                    return stocks("[]");
                });

        loader.reload();

        TossListedStock samsung = universe.findBySymbol("005930").orElseThrow();
        assertThat(samsung.name()).isEqualTo("삼성전자");
        assertThat(samsung.marketCountry()).isEqualTo("KR");
        assertThat(samsung.currency()).isEqualTo("KRW");
        assertThat(samsung.locSupported()).isFalse();

        TossListedStock apple = universe.findBySymbol("AAPL").orElseThrow();
        assertThat(apple.marketCountry()).isEqualTo("US");
        assertThat(apple.currency()).isEqualTo("USD");
        assertThat(apple.locSupported()).isTrue();
    }

    @Test
    @DisplayName("한 마켓이 실패해도 나머지 마켓으로 인덱스를 만든다")
    void survivesSingleMarketFailure() {
        given(tossInvestClient.get(eq(TossAccountOwner.ME), eq(STOCKS_ALL_PATH), any()))
                .willAnswer(invocation -> {
                    Map<String, String> params = invocation.getArgument(2);
                    if ("KOSDAQ".equals(params.get("market"))) {
                        throw new IllegalStateException("코스닥 조회 실패");
                    }
                    if ("KOSPI".equals(params.get("market"))) {
                        return stocks("""
                                [{"symbol":"005930","name":"삼성전자","securityType":"STOCK","isCommonShare":true}]
                                """);
                    }
                    return stocks("[]");
                });

        loader.reload();

        assertThat(universe.isReady()).isTrue();
        assertThat(universe.findBySymbol("005930")).isPresent();
    }

    @Test
    @DisplayName("모든 마켓이 실패하면 이미 갖고 있던 인덱스를 유지한다")
    void keepsPreviousIndexWhenEveryMarketFails() {
        // 어제 목록으로 검색되는 편이, 검색이 통째로 죽는 것보다 낫다.
        universe.replace(List.of(TossListedStock.of("005930", "삼성전자", "KOSPI", "STOCK", true)));
        given(tossInvestClient.get(eq(TossAccountOwner.ME), eq(STOCKS_ALL_PATH), any()))
                .willThrow(new IllegalStateException("전면 장애"));

        loader.reload();

        assertThat(universe.isReady()).isTrue();
        assertThat(universe.findBySymbol("005930")).isPresent();
    }

    @Test
    @DisplayName("연동된 계좌가 없으면 아무 호출도 하지 않는다")
    void doesNothingWithoutConfiguredAccounts() {
        // 테스트·로컬 환경에는 toss-invest 설정이 없다. 부팅 워밍업이 거기서 예외를 뿜으면 안 된다.
        properties.setAccounts(new java.util.ArrayList<>());

        loader.reload();

        verify(tossInvestClient, never()).get(any(), any(), any());
        assertThat(universe.isReady()).isFalse();
    }

    @Test
    @DisplayName("부팅 워밍업은 실패해도 예외를 밖으로 내보내지 않는다")
    void warmUpSwallowsFailures() {
        // 여기서 예외가 새면 @Async 스레드에서 스택트레이스만 남고, @Scheduled 는 다음 실행이 막힌다.
        given(tossInvestClient.get(any(), any(), any())).willThrow(new IllegalStateException("장애"));

        loader.warmUpOnStartup();
        loader.refreshDaily();

        assertThat(universe.isReady()).isFalse();
    }

    @Test
    @DisplayName("심볼이나 이름이 비어 있는 항목은 버린다")
    void skipsEntriesWithoutSymbolOrName() {
        given(tossInvestClient.get(eq(TossAccountOwner.ME), eq(STOCKS_ALL_PATH), any()))
                .willAnswer(invocation -> {
                    Map<String, String> params = invocation.getArgument(2);
                    if ("KOSPI".equals(params.get("market"))) {
                        return stocks("""
                                [{"symbol":"005930","name":"삼성전자","securityType":"STOCK","isCommonShare":true},
                                 {"symbol":"","name":"이름만 있음","securityType":"STOCK","isCommonShare":true},
                                 {"symbol":"000660","name":"","securityType":"STOCK","isCommonShare":true}]
                                """);
                    }
                    return stocks("[]");
                });

        loader.reload();

        assertThat(universe.entries()).hasSize(1);
        assertThat(universe.findBySymbol("000660")).isEmpty();
    }
}
