package com.behcm.domain.tossstock.service;

import com.behcm.domain.tossstock.service.TossOrderHistoryReader.OrderHistory;
import com.behcm.domain.tossstock.service.TossRealizedProfitCalculator.Fill;
import com.behcm.domain.tossstock.service.TossRealizedProfitCalculator.TradeSide;
import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

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

    @InjectMocks
    private TossOrderHistoryReader reader;

    private JsonNode json(String text) {
        return objectMapper.readTree(text);
    }

    private String order(String symbol, String side, String filledQuantity, String filledAmount, String filledAt) {
        return """
                {
                  "orderId": "o-%s-%s",
                  "symbol": "%s",
                  "side": "%s",
                  "status": "FILLED",
                  "currency": "KRW",
                  "orderedAt": "2026-01-01T09:00:00+09:00",
                  "execution": {
                    "filledQuantity": "%s",
                    "filledAmount": "%s",
                    "commission": "15",
                    "tax": null,
                    "filledAt": %s
                  }
                }
                """.formatted(symbol, side, symbol, side, filledQuantity, filledAmount, filledAt);
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
}
