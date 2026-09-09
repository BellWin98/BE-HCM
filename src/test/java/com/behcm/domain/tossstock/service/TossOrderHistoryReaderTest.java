package com.behcm.domain.tossstock.service;

import com.behcm.domain.tossstock.service.TossOrderHistoryReader.OrderHistory;
import com.behcm.domain.tossstock.service.TossRealizedProfitCalculator.Fill;
import com.behcm.domain.tossstock.service.TossRealizedProfitCalculator.TradeSide;
import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TossOrderHistoryReaderTest {

    private static final String ORDERS_PATH = "/api/v1/orders";
    private static final String STOCKS_PATH = "/api/v1/stocks";
    private static final TossAccountOwner OWNER = TossAccountOwner.ME;
    private static final Long ACCOUNT_SEQ = 1L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TossInvestClient tossInvestClient;

    @Mock
    private TossOpenOrderReader openOrderReader;

    // 캐시 동작이 결과를 바꾸므로 목이 아니라 진짜 캐시를 쓴다. 테스트마다 새로 만들어 격리한다.
    private final CacheManager cacheManager = new ConcurrentMapCacheManager("tossOrderHistory");

    private TossOrderHistoryReader reader;

    @BeforeEach
    void setUp() {
        reader = new TossOrderHistoryReader(tossInvestClient, openOrderReader, cacheManager);
    }

    private JsonNode json(String text) {
        return objectMapper.readTree(text);
    }

    private String order(String symbol, String side, String filledQuantity, String filledAmount, String filledAt) {
        return order("o-%s-%s".formatted(symbol, side), symbol, side, "FILLED",
                filledQuantity, filledAmount, "2026-01-01T09:00:00+09:00", filledAt);
    }

    /** 주문 식별자·상태·접수시각까지 지정하는 형태. 증분 동기화 테스트가 이 축들을 움직인다. */
    private String order(String orderId, String symbol, String side, String status,
                         String filledQuantity, String filledAmount, String orderedAt, String filledAt) {
        return """
                {
                  "orderId": "%s",
                  "symbol": "%s",
                  "side": "%s",
                  "status": "%s",
                  "currency": "KRW",
                  "orderedAt": "%s",
                  "execution": {
                    "filledQuantity": "%s",
                    "filledAmount": "%s",
                    "commission": "15",
                    "tax": null,
                    "filledAt": %s
                  }
                }
                """.formatted(orderId, symbol, side, status, orderedAt, filledQuantity, filledAmount, filledAt);
    }

    private String page(String... orders) {
        return """
                {"orders": [%s], "nextCursor": null, "hasNext": false}
                """.formatted(String.join(",", orders));
    }

    /** KST 오프셋이 붙은 ISO 시각. 테스트가 실제 오늘 날짜를 기준으로 창을 계산한다. */
    private String at(LocalDate date, String time) {
        return date + "T" + time + "+09:00";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> capturedOrderParams() {
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(tossInvestClient, org.mockito.Mockito.atLeastOnce())
                .get(eq(OWNER), eq(ORDERS_PATH), captor.capture(), eq(ACCOUNT_SEQ));
        return captor.getAllValues();
    }

    private void stubOrders(JsonNode... pages) {
        given(tossInvestClient.get(eq(OWNER), eq(ORDERS_PATH), any(), eq(ACCOUNT_SEQ)))
                .willReturn(pages[0], java.util.Arrays.copyOfRange(pages, 1, pages.length));
    }

    private void stubNames(String namesJson) {
        given(tossInvestClient.get(eq(OWNER), eq(STOCKS_PATH), any())).willReturn(json(namesJson));
    }

    @Test
    @DisplayName("체결 수량이 0인 주문(취소·거부)은 제외한다")
    void readAll_skipsOrdersWithNoFill() {
        stubOrders(json("""
                {"orders": [
                  %s,
                  %s
                ], "nextCursor": null, "hasNext": false}
                """.formatted(
                order("005930", "BUY", "10", "10000", "\"2026-01-02T09:05:00+09:00\""),
                order("000660", "BUY", "0", "0", "null")
        )));
        stubNames("""
                [{"symbol": "005930", "name": "삼성전자"}]
                """);

        OrderHistory history = reader.readAll(OWNER, ACCOUNT_SEQ);

        assertThat(history.fills()).hasSize(1);
        assertThat(history.fills().get(0).symbol()).isEqualTo("005930");
    }

    @Test
    @DisplayName("hasNext가 true면 커서로 다음 페이지를 이어 읽는다")
    void readAll_followsCursorUntilExhausted() {
        stubOrders(
                json("""
                        {"orders": [%s], "nextCursor": "cursor-2", "hasNext": true}
                        """.formatted(order("005930", "BUY", "10", "10000", "\"2026-01-02T09:05:00+09:00\""))),
                json("""
                        {"orders": [%s], "nextCursor": null, "hasNext": false}
                        """.formatted(order("005930", "SELL", "10", "12000", "\"2026-01-03T09:05:00+09:00\"")))
        );
        stubNames("""
                [{"symbol": "005930", "name": "삼성전자"}]
                """);

        OrderHistory history = reader.readAll(OWNER, ACCOUNT_SEQ);

        assertThat(history.fills()).hasSize(2);
        verify(tossInvestClient, times(2)).get(eq(OWNER), eq(ORDERS_PATH), any(), eq(ACCOUNT_SEQ));
    }

    @Test
    @DisplayName("응답 순서와 무관하게 체결 시각 오름차순으로 정렬한다")
    void readAll_sortsFillsByExecutionTimeAscending() {
        // 이동평균 원가는 순서에 의존하므로 최신순으로 오는 응답을 그대로 쓰면 안 된다.
        stubOrders(json("""
                {"orders": [%s, %s], "nextCursor": null, "hasNext": false}
                """.formatted(
                order("005930", "SELL", "10", "12000", "\"2026-03-10T09:05:00+09:00\""),
                order("005930", "BUY", "10", "10000", "\"2026-01-02T09:05:00+09:00\"")
        )));
        stubNames("[]");

        List<Fill> fills = reader.readAll(OWNER, ACCOUNT_SEQ).fills();

        assertThat(fills.get(0).side()).isEqualTo(TradeSide.BUY);
        assertThat(fills.get(0).tradeDate()).isEqualTo("2026-01-02");
        assertThat(fills.get(1).side()).isEqualTo(TradeSide.SELL);
        assertThat(fills.get(1).tradeDate()).isEqualTo("2026-03-10");
    }

    @Test
    @DisplayName("체결 시각을 시·분까지 보존한다")
    void readAll_keepsExecutionTimeOfDay() {
        // 화면이 거래일시를 시·분까지 보여주려면 날짜로 잘라 버리면 안 된다.
        stubOrders(json("""
                {"orders": [%s], "nextCursor": null, "hasNext": false}
                """.formatted(order("005930", "BUY", "10", "10000", "\"2026-01-02T14:32:11+09:00\""))));
        stubNames("[]");

        Fill fill = reader.readAll(OWNER, ACCOUNT_SEQ).fills().get(0);

        assertThat(fill.executedAt()).isEqualTo(LocalDateTime.parse("2026-01-02T14:32:11"));
        assertThat(fill.tradeDate()).isEqualTo("2026-01-02");
    }

    @Test
    @DisplayName("체결 시각이 없으면 주문 시각을 대신 쓴다")
    void readAll_fallsBackToOrderedAtWhenFilledAtIsNull() {
        stubOrders(json("""
                {"orders": [%s], "nextCursor": null, "hasNext": false}
                """.formatted(order("005930", "BUY", "10", "10000", "null"))));
        stubNames("[]");

        List<Fill> fills = reader.readAll(OWNER, ACCOUNT_SEQ).fills();

        assertThat(fills.get(0).tradeDate()).isEqualTo("2026-01-01");
    }

    @Test
    @DisplayName("체결 금액·수수료를 파싱하고 null 세금은 0으로 다룬다")
    void readAll_parsesAmountsAndTreatsNullTaxAsZero() {
        stubOrders(json("""
                {"orders": [%s], "nextCursor": null, "hasNext": false}
                """.formatted(order("005930", "BUY", "10", "10000", "\"2026-01-02T09:05:00+09:00\""))));
        stubNames("[]");

        Fill fill = reader.readAll(OWNER, ACCOUNT_SEQ).fills().get(0);

        assertThat(fill.quantity()).isEqualByComparingTo("10");
        assertThat(fill.amount()).isEqualByComparingTo("10000");
        assertThat(fill.commission()).isEqualByComparingTo("15");
        assertThat(fill.tax()).isEqualByComparingTo("0");
        assertThat(fill.currency()).isEqualTo("KRW");
    }

    @Test
    @DisplayName("종목명을 종목 기본정보 API로 채운다")
    void readAll_resolvesSymbolNames() {
        stubOrders(json("""
                {"orders": [%s], "nextCursor": null, "hasNext": false}
                """.formatted(order("005930", "BUY", "10", "10000", "\"2026-01-02T09:05:00+09:00\""))));
        stubNames("""
                [{"symbol": "005930", "name": "삼성전자"}]
                """);

        OrderHistory history = reader.readAll(OWNER, ACCOUNT_SEQ);

        assertThat(history.names()).containsEntry("005930", "삼성전자");
    }

    @Test
    @DisplayName("종목명 조회가 실패해도 체결 내역은 그대로 반환한다")
    void readAll_whenNameLookupFails_stillReturnsFills() {
        stubOrders(json("""
                {"orders": [%s], "nextCursor": null, "hasNext": false}
                """.formatted(order("005930", "BUY", "10", "10000", "\"2026-01-02T09:05:00+09:00\""))));
        given(tossInvestClient.get(eq(OWNER), eq(STOCKS_PATH), any()))
                .willThrow(new RuntimeException("stocks api down"));

        OrderHistory history = reader.readAll(OWNER, ACCOUNT_SEQ);

        assertThat(history.fills()).hasSize(1);
        assertThat(history.names()).isEmpty();
    }

    @Test
    @DisplayName("주문이 없으면 종목명 조회를 호출하지 않는다")
    void readAll_withNoOrders_doesNotCallStocksApi() {
        stubOrders(json("""
                {"orders": [], "nextCursor": null, "hasNext": false}
                """));

        OrderHistory history = reader.readAll(OWNER, ACCOUNT_SEQ);

        assertThat(history.fills()).isEmpty();
        assertThat(history.names()).isEqualTo(Map.of());
        verify(tossInvestClient, times(0)).get(eq(OWNER), eq(STOCKS_PATH), any());
    }

    // ---------------------------------------------------------------------
    // 증분 동기화 — OPEN 워터마크
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("첫 조회는 캐시가 없으므로 from 없이 전체 기간을 읽는다")
    void readAll_firstCall_readsWholeHistoryWithoutFrom() {
        stubOrders(json(page(order("005930", "BUY", "10", "10000", "\"2026-01-02T09:05:00+09:00\""))));
        stubNames("[]");

        reader.readAll(OWNER, ACCOUNT_SEQ);

        assertThat(capturedOrderParams()).hasSize(1);
        assertThat(capturedOrderParams().get(0)).doesNotContainKey("from");
        assertThat(capturedOrderParams().get(0)).containsEntry("status", "CLOSED");
    }

    @Test
    @DisplayName("두 번째 조회는 미체결 주문 중 가장 오래된 접수일부터만 다시 읽는다")
    void readAll_secondCall_refetchesOnlyFromOpenOrderWatermark() {
        LocalDate today = LocalDate.now();
        LocalDate oldestOpen = today.minusDays(20);

        stubOrders(
                json(page(order("o1", "005930", "BUY", "FILLED", "10", "10000",
                        at(today.minusDays(40), "09:00:00"), "\"%s\"".formatted(at(today.minusDays(40), "09:05:00"))))),
                json(page())
        );
        stubNames("[]");
        reader.readAll(OWNER, ACCOUNT_SEQ);

        // 20일 전에 낸 지정가가 아직 살아 있다 — 그 주문이 오늘 체결될 수 있으므로 재조회 하한이 된다.
        given(openOrderReader.earliestOrderedAt(OWNER, ACCOUNT_SEQ))
                .willReturn(Optional.of(oldestOpen.atTime(9, 30)));

        reader.readAll(OWNER, ACCOUNT_SEQ);

        List<Map<String, String>> calls = capturedOrderParams();
        assertThat(calls).hasSize(2);
        assertThat(calls.get(1)).containsEntry("from", oldestOpen.toString());
    }

    @Test
    @DisplayName("미체결 주문이 없으면 안전 여유(3일) 창만 다시 읽는다")
    void readAll_withNoOpenOrders_refetchesSafetyMarginWindowOnly() {
        stubOrders(json(page()), json(page()));
        reader.readAll(OWNER, ACCOUNT_SEQ);

        given(openOrderReader.earliestOrderedAt(OWNER, ACCOUNT_SEQ)).willReturn(Optional.empty());

        reader.readAll(OWNER, ACCOUNT_SEQ);

        assertThat(capturedOrderParams().get(1))
                .containsEntry("from", LocalDate.now().minusDays(3).toString());
    }

    @Test
    @DisplayName("미체결 조회가 실패해도 보수적인 창으로 계속 동기화한다")
    void readAll_whenOpenOrderLookupFails_fallsBackToSafetyMarginWindow() {
        stubOrders(json(page()), json(page()));
        reader.readAll(OWNER, ACCOUNT_SEQ);

        given(openOrderReader.earliestOrderedAt(OWNER, ACCOUNT_SEQ))
                .willThrow(new RuntimeException("open orders api down"));

        reader.readAll(OWNER, ACCOUNT_SEQ);

        // 실패했다고 전체를 다시 읽지도, 예외를 밖으로 내보내지도 않는다.
        assertThat(capturedOrderParams().get(1))
                .containsEntry("from", LocalDate.now().minusDays(3).toString());
    }

    @Test
    @DisplayName("워터마크 이전 체결은 다시 읽지 않고 캐시에서 그대로 쓴다")
    void readAll_keepsFillsOlderThanWatermarkWithoutRefetching() {
        LocalDate today = LocalDate.now();
        LocalDate old = today.minusDays(40);

        stubOrders(
                json(page(
                        order("o-old", "005930", "BUY", "FILLED", "10", "10000",
                                at(old, "09:00:00"), "\"%s\"".formatted(at(old, "09:05:00"))),
                        order("o-new", "000660", "BUY", "FILLED", "5", "5000",
                                at(today, "09:00:00"), "\"%s\"".formatted(at(today, "09:05:00"))))),
                // 재조회 창(오늘-3일)에는 최근 주문만 들어온다. 오래된 주문은 응답에 없다.
                json(page(order("o-new", "000660", "BUY", "FILLED", "5", "5000",
                        at(today, "09:00:00"), "\"%s\"".formatted(at(today, "09:05:00")))))
        );
        stubNames("[]");
        reader.readAll(OWNER, ACCOUNT_SEQ);

        given(openOrderReader.earliestOrderedAt(OWNER, ACCOUNT_SEQ)).willReturn(Optional.empty());

        OrderHistory second = reader.readAll(OWNER, ACCOUNT_SEQ);

        assertThat(second.fills()).hasSize(2);
        assertThat(second.fills()).extracting(Fill::symbol).containsExactly("005930", "000660");
    }

    @Test
    @DisplayName("부분 체결이 갱신되면 같은 주문이 중복되지 않고 교체된다")
    void readAll_replacesPartiallyFilledOrderInsteadOfAppending() {
        // PARTIAL_FILLED 는 OPEN·CLOSED 두 그룹에 모두 속한다. 같은 orderId 로 체결 수량이 늘어나는데,
        // 이때 덧붙이면 같은 체결이 두 번 계산되어 실현손익이 두 배로 잡힌다.
        LocalDate today = LocalDate.now();

        stubOrders(
                json(page(order("o1", "005930", "SELL", "PARTIAL_FILLED", "2", "24000",
                        at(today, "09:00:00"), "\"%s\"".formatted(at(today, "09:05:00"))))),
                json(page(order("o1", "005930", "SELL", "FILLED", "5", "60000",
                        at(today, "09:00:00"), "\"%s\"".formatted(at(today, "09:40:00")))))
        );
        stubNames("[]");

        OrderHistory first = reader.readAll(OWNER, ACCOUNT_SEQ);
        assertThat(first.fills()).hasSize(1);
        assertThat(first.fills().get(0).quantity()).isEqualByComparingTo("2");

        given(openOrderReader.earliestOrderedAt(OWNER, ACCOUNT_SEQ)).willReturn(Optional.empty());

        OrderHistory second = reader.readAll(OWNER, ACCOUNT_SEQ);

        assertThat(second.fills()).hasSize(1);
        assertThat(second.fills().get(0).quantity()).isEqualByComparingTo("5");
        assertThat(second.fills().get(0).amount()).isEqualByComparingTo("60000");
    }

    @Test
    @DisplayName("이미 이름을 아는 심볼은 종목명 API 를 다시 부르지 않는다")
    void readAll_doesNotReResolveKnownSymbolNames() {
        LocalDate today = LocalDate.now();
        stubOrders(
                json(page(order("o1", "005930", "BUY", "FILLED", "10", "10000",
                        at(today, "09:00:00"), "\"%s\"".formatted(at(today, "09:05:00"))))),
                json(page(order("o1", "005930", "BUY", "FILLED", "10", "10000",
                        at(today, "09:00:00"), "\"%s\"".formatted(at(today, "09:05:00")))))
        );
        stubNames("""
                [{"symbol": "005930", "name": "삼성전자"}]
                """);
        reader.readAll(OWNER, ACCOUNT_SEQ);

        given(openOrderReader.earliestOrderedAt(OWNER, ACCOUNT_SEQ)).willReturn(Optional.empty());

        OrderHistory second = reader.readAll(OWNER, ACCOUNT_SEQ);

        assertThat(second.names()).containsEntry("005930", "삼성전자");
        verify(tossInvestClient, times(1)).get(eq(OWNER), eq(STOCKS_PATH), any());
    }

    @Test
    @DisplayName("캐시를 비우면 다시 전체 기간을 읽는다")
    void readAll_afterCacheEviction_readsWholeHistoryAgain() {
        stubOrders(json(page()), json(page()));
        reader.readAll(OWNER, ACCOUNT_SEQ);

        cacheManager.getCache("tossOrderHistory").evict(OWNER);

        reader.readAll(OWNER, ACCOUNT_SEQ);

        assertThat(capturedOrderParams().get(1)).doesNotContainKey("from");
    }
}
