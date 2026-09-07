package com.behcm.domain.tossstock.service;

import com.behcm.domain.tossstock.dto.TossOrderRequest;
import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossOrderClient.TossOrderCommand;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * 주문 검증.
 *
 * <p>여기서 막는 것과 토스에 맡기는 것의 기준은 하나다 — <b>확인한 순간과 주문이 닿는 순간 사이에
 * 변하는 값인가</b>. 잔고·상하한가·장 운영시간·호가 단위는 변하므로 우리가 미리 막으면
 * 실제로는 가능한 주문을 거부하게 된다(오탐). 반대로 "국내 종목에 LOC" 같은 건 변하지 않으므로
 * 굳이 왕복해서 400 을 받아 올 이유가 없다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TossOrderValidatorTest {

    private static final TossAccountOwner OWNER = TossAccountOwner.ME;

    @Mock
    private TossStockLookup stockLookup;

    private TossOrderValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TossOrderValidator(stockLookup);
        given(stockLookup.resolve(eq(OWNER), eq("005930")))
                .willReturn(TossListedStock.of("005930", "삼성전자", "KOSPI", "STOCK", true));
        given(stockLookup.resolve(eq(OWNER), eq("AAPL")))
                .willReturn(TossListedStock.of("AAPL", "애플", "NASDAQ", "STOCK", true));
    }

    private TossOrderRequest request(String symbol, String orderType, String quantity, String price) {
        TossOrderRequest request = new TossOrderRequest();
        request.setOwner("ME");
        request.setSymbol(symbol);
        request.setSide("BUY");
        request.setOrderType(orderType);
        request.setQuantity(quantity);
        request.setPrice(price);
        request.setClientOrderId("order-key-1");
        return request;
    }

    private TossOrderRequest krLimit() {
        return request("005930", "LIMIT", "10", "70000");
    }

    private TossOrderRequest usLimit() {
        return request("AAPL", "LIMIT", "10", "185.50");
    }

    private void assertRejectedWith(ErrorCode expected, ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("국내 지정가 주문은 통과하고 토스 명령으로 변환된다")
    void acceptsKoreanLimitOrder() {
        TossOrderValidator.ValidatedOrder validated = validator.validate(OWNER, krLimit());

        TossOrderCommand command = validated.command();
        assertThat(command.symbol()).isEqualTo("005930");
        assertThat(command.side()).isEqualTo("BUY");
        assertThat(command.orderType()).isEqualTo("LIMIT");
        assertThat(command.quantity()).isEqualByComparingTo("10");
        assertThat(command.price()).isEqualByComparingTo("70000");
        assertThat(validated.stock().name()).isEqualTo("삼성전자");
    }

    @Test
    @DisplayName("국내 종목에 LOC(CLS)을 지정하면 토스를 부르지 않고 거절한다")
    void rejectsClsForKoreanStock() {
        // 토스도 400 으로 막지만 그건 왕복 한 번을 버리는 일이다.
        // 종목의 시장은 변하지 않으므로 여기서 끝내는 편이 맞다.
        TossOrderRequest request = krLimit();
        request.setTimeInForce("CLS");

        assertRejectedWith(ErrorCode.TOSS_ORDER_LOC_NOT_SUPPORTED, () -> validator.validate(OWNER, request));
    }

    @Test
    @DisplayName("미국 종목의 LOC(CLS)은 통과한다")
    void acceptsClsForUsStock() {
        TossOrderRequest request = usLimit();
        request.setTimeInForce("CLS");

        assertThat(validator.validate(OWNER, request).command().timeInForce()).isEqualTo("CLS");
    }

    @Test
    @DisplayName("시장가에는 CLS 를 쓸 수 없다 — LOC 은 지정가 조합이다")
    void rejectsClsForMarketOrder() {
        TossOrderRequest request = request("AAPL", "MARKET", "10", null);
        request.setTimeInForce("CLS");

        assertRejectedWith(ErrorCode.TOSS_ORDER_LOC_NOT_SUPPORTED, () -> validator.validate(OWNER, request));
    }

    @Test
    @DisplayName("지정가인데 가격이 없으면 거절한다")
    void rejectsLimitWithoutPrice() {
        assertRejectedWith(ErrorCode.TOSS_ORDER_INVALID,
                () -> validator.validate(OWNER, request("005930", "LIMIT", "10", null)));
    }

    @Test
    @DisplayName("시장가인데 가격이 있으면 거절한다")
    void rejectsMarketWithPrice() {
        // 토스는 시장가에 price 가 실려 오면 400 을 낸다. 화면 상태가 꼬였다는 신호이기도 하다.
        assertRejectedWith(ErrorCode.TOSS_ORDER_INVALID,
                () -> validator.validate(OWNER, request("005930", "MARKET", "10", "70000")));
    }

    @Test
    @DisplayName("수량이 0이면 거절한다")
    void rejectsZeroQuantity() {
        assertRejectedWith(ErrorCode.TOSS_ORDER_INVALID,
                () -> validator.validate(OWNER, request("005930", "LIMIT", "0", "70000")));
    }

    @Test
    @DisplayName("국내 종목의 가격에 소수점이 있으면 거절한다")
    void rejectsFractionalPriceForKoreanStock() {
        assertRejectedWith(ErrorCode.TOSS_ORDER_INVALID,
                () -> validator.validate(OWNER, request("005930", "LIMIT", "10", "70000.5")));
    }

    @Test
    @DisplayName("1달러 이상 미국 종목은 소수점 둘째 자리까지만 허용한다")
    void rejectsTooManyDecimalsAboveOneDollar() {
        assertRejectedWith(ErrorCode.TOSS_ORDER_INVALID,
                () -> validator.validate(OWNER, request("AAPL", "LIMIT", "10", "185.505")));
    }

    @Test
    @DisplayName("1달러 미만 미국 종목은 소수점 넷째 자리까지 허용한다")
    void allowsFourDecimalsBelowOneDollar() {
        assertThat(validator.validate(OWNER, request("AAPL", "LIMIT", "10", "0.1234")).command().price())
                .isEqualByComparingTo("0.1234");
    }

    @Test
    @DisplayName("timeInForce 를 주지 않으면 DAY 로 채운다")
    void defaultsTimeInForceToDay() {
        assertThat(validator.validate(OWNER, krLimit()).command().timeInForce()).isEqualTo("DAY");
    }

    @Test
    @DisplayName("멱등키가 없으면 서버가 채워 준다")
    void fillsClientOrderIdWhenMissing() {
        // 프론트가 만드는 게 원칙이지만, 없다고 멱등성 없이 내보내면 재시도가 곧 이중 주문이 된다.
        TossOrderRequest request = krLimit();
        request.setClientOrderId(null);

        String generated = validator.validate(OWNER, request).command().clientOrderId();

        assertThat(generated).isNotBlank().matches("^[a-zA-Z0-9\\-_]{1,36}$");
    }

    @Test
    @DisplayName("프론트가 보낸 멱등키는 그대로 쓴다")
    void keepsProvidedClientOrderId() {
        assertThat(validator.validate(OWNER, krLimit()).command().clientOrderId()).isEqualTo("order-key-1");
    }

    @Test
    @DisplayName("찾을 수 없는 종목은 종목 없음으로 거절한다")
    void rejectsUnknownSymbol() {
        given(stockLookup.resolve(any(), eq("999999")))
                .willThrow(new CustomException(ErrorCode.TOSS_STOCK_NOT_FOUND));

        assertRejectedWith(ErrorCode.TOSS_STOCK_NOT_FOUND,
                () -> validator.validate(OWNER, request("999999", "LIMIT", "10", "70000")));
    }

    @Test
    @DisplayName("고액 주문 확인 플래그는 그대로 전달한다")
    void passesThroughHighValueConfirmation() {
        TossOrderRequest request = krLimit();
        request.setConfirmHighValueOrder(true);

        assertThat(validator.validate(OWNER, request).command().confirmHighValueOrder()).isTrue();
    }
}
