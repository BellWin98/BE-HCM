package com.behcm.domain.tossstock.service;

import com.behcm.domain.tossstock.dto.TossOpenOrderResponse;
import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
 * 미체결 주문 목록.
 *
 * <p>주문을 낼 수 있게 된 이상, 낸 것이 살아 있는지 볼 수단이 정확해야 한다 —
 * 여기가 틀리면 사용자는 같은 주문을 한 번 더 낸다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TossOpenOrderReaderTest {

    private static final String ORDERS_PATH = "/api/v1/orders";
    private static final String STOCKS_PATH = "/api/v1/stocks";
    private static final TossAccountOwner OWNER = TossAccountOwner.ME;
    private static final Long ACCOUNT_SEQ = 1L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TossInvestClient tossInvestClient;

    @InjectMocks
    private TossOpenOrderReader reader;

    private JsonNode json(String text) {
        return objectMapper.readTree(text);
    }

    private void stubOrders(String ordersJson) {
        given(tossInvestClient.get(eq(OWNER), eq(ORDERS_PATH), any(), eq(ACCOUNT_SEQ)))
                .willReturn(json(ordersJson));
    }

    private void stubNames(String namesJson) {
        given(tossInvestClient.get(eq(OWNER), eq(STOCKS_PATH), any())).willReturn(json(namesJson));
    }

    private String order(String orderId, String symbol, String orderType,
                         String timeInForce, String status, String quantity, String filled) {
        return """
                {
                  "orderId": "%s",
                  "symbol": "%s",
                  "side": "BUY",
                  "orderType": "%s",
                  "timeInForce": "%s",
                  "status": "%s",
                  "price": "70000",
                  "quantity": "%s",
                  "currency": "KRW",
                  "orderedAt": "2026-09-07T09:30:00+09:00",
                  "execution": { "filledQuantity": "%s" }
                }
                """.formatted(orderId, symbol, orderType, timeInForce, status, quantity, filled);
    }

    private List<TossOpenOrderResponse> read() {
        return reader.read(OWNER, ACCOUNT_SEQ);
    }

    @Test
    @DisplayName("status=OPEN 으로 조회한다")
    void queriesOpenStatus() {
        stubOrders("{\"orders\": []}");

        read();

        ArgumentCaptor<Map<String, String>> params = ArgumentCaptor.captor();
        verify(tossInvestClient).get(eq(OWNER), eq(ORDERS_PATH), params.capture(), eq(ACCOUNT_SEQ));
        assertThat(params.getValue()).containsEntry("status", "OPEN");
    }

    @Test
    @DisplayName("미체결이 없으면 종목명 조회를 하지 않는다")
    void skipsNameLookupWhenEmpty() {
        stubOrders("{\"orders\": []}");

        assertThat(read()).isEmpty();
        verify(tossInvestClient, never()).get(any(), eq(STOCKS_PATH), any());
    }

    @Test
    @DisplayName("주문에 없는 종목명을 종목 기본정보로 채운다")
    void resolvesStockNames() {
        stubOrders("{\"orders\": [%s]}".formatted(
                order("o-1", "005930", "LIMIT", "DAY", "PENDING", "10", "0")));
        stubNames("[{\"symbol\": \"005930\", \"name\": \"삼성전자\"}]");

        assertThat(read().get(0).name()).isEqualTo("삼성전자");
    }

    @Test
    @DisplayName("종목명 조회가 실패해도 목록은 심볼로 대체해 보여준다")
    void fallsBackToSymbolWhenNameLookupFails() {
        // 이름은 표시용 부가정보다. 그것 때문에 미체결 목록 전체가 사라지면 안 된다.
        stubOrders("{\"orders\": [%s]}".formatted(
                order("o-1", "005930", "LIMIT", "DAY", "PENDING", "10", "0")));
        given(tossInvestClient.get(eq(OWNER), eq(STOCKS_PATH), any()))
                .willThrow(new IllegalStateException("종목명 조회 실패"));

        assertThat(read().get(0).name()).isEqualTo("005930");
    }

    @Test
    @DisplayName("LIMIT + CLS 조합은 loc=true 로 내려간다")
    void marksLocOrders() {
        // LOC 은 토스 응답에 별도 필드가 없다. 화면이 매번 다시 판정하지 않도록 여기서 풀어 준다.
        stubOrders("{\"orders\": [%s, %s]}".formatted(
                order("o-1", "AAPL", "LIMIT", "CLS", "PENDING", "10", "0"),
                order("o-2", "005930", "LIMIT", "DAY", "PENDING", "10", "0")));
        stubNames("[]");

        List<TossOpenOrderResponse> orders = read();

        assertThat(orders.get(0).loc()).isTrue();
        assertThat(orders.get(1).loc()).isFalse();
    }

    @Test
    @DisplayName("부분 체결은 잔량을 계산하고 취소할 수 있다")
    void computesRemainingQuantityForPartialFills() {
        stubOrders("{\"orders\": [%s]}".formatted(
                order("o-1", "005930", "LIMIT", "DAY", "PARTIAL_FILLED", "10", "3")));
        stubNames("[]");

        TossOpenOrderResponse order = read().get(0);

        assertThat(order.quantity()).isEqualByComparingTo("10");
        assertThat(order.filledQuantity()).isEqualByComparingTo("3");
        assertThat(order.remainingQuantity()).isEqualByComparingTo("7");
        assertThat(order.cancelable()).isTrue();
    }

    @Test
    @DisplayName("이미 취소 요청이 나간 주문에는 취소 버튼을 그리지 않는다")
    void pendingCancelIsNotCancelable() {
        stubOrders("{\"orders\": [%s]}".formatted(
                order("o-1", "005930", "LIMIT", "DAY", "PENDING_CANCEL", "10", "0")));
        stubNames("[]");

        assertThat(read().get(0).cancelable()).isFalse();
    }

    @Test
    @DisplayName("체결 정보가 없는 주문의 잔량은 주문 수량 그대로다")
    void treatsMissingExecutionAsUnfilled() {
        stubOrders("""
                {"orders": [{
                  "orderId": "o-1", "symbol": "005930", "side": "BUY",
                  "orderType": "LIMIT", "timeInForce": "DAY", "status": "PENDING",
                  "price": "70000", "quantity": "10", "currency": "KRW",
                  "orderedAt": "2026-09-07T09:30:00+09:00"
                }]}
                """);
        stubNames("[]");

        TossOpenOrderResponse order = read().get(0);

        assertThat(order.filledQuantity()).isEqualByComparingTo("0");
        assertThat(order.remainingQuantity()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("시장가 주문의 가격은 null 로 남긴다")
    void keepsMarketOrderPriceNull() {
        // 0 으로 채우면 "0원에 주문했다"로 읽힌다. 시장가는 가격이 없는 것이 사실이다.
        stubOrders("""
                {"orders": [{
                  "orderId": "o-1", "symbol": "005930", "side": "BUY",
                  "orderType": "MARKET", "timeInForce": "DAY", "status": "PENDING",
                  "price": null, "quantity": "10", "currency": "KRW",
                  "orderedAt": "2026-09-07T09:30:00+09:00",
                  "execution": { "filledQuantity": "0" }
                }]}
                """);
        stubNames("[]");

        assertThat(read().get(0).price()).isNull();
    }
}
