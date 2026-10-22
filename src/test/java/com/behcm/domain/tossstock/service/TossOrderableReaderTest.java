package com.behcm.domain.tossstock.service;

import com.behcm.domain.tossstock.dto.TossOrderableResponse;
import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * 주문 화면 값 묶음에 보유 종목의 평균단가·보유수량을 함께 싣는다.
 *
 * <p>화면은 이 두 값으로 "매수 후 예상 평균단가"와 "매도 예상 손익"을 계산한다.
 * 보유하지 않은 종목이면 <b>null</b> 이어야 한다 — 0 으로 내려가면 화면이 "평단 0원"을 그린다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TossOrderableReaderTest {

    private static final String PRICES_PATH = "/api/v1/prices";
    private static final TossAccountOwner OWNER = TossAccountOwner.ME;
    private static final Long ACCOUNT_SEQ = 1L;
    private static final TossListedStock SAMSUNG =
            TossListedStock.of("005930", "삼성전자", "KOSPI", "STOCK", true);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TossInvestClient tossInvestClient;

    @Mock
    private TossHoldingsReader holdingsReader;

    @Mock
    private TossCommissionReader commissionReader;

    private TossOrderableReader reader;
    private final TossApiExecutor apiExecutor = new TossApiExecutor();

    @BeforeEach
    void setUp() {
        reader = new TossOrderableReader(tossInvestClient, holdingsReader, apiExecutor,
                commissionReader, java.time.Clock.systemDefaultZone());
        given(tossInvestClient.get(eq(OWNER), eq(PRICES_PATH), any()))
                .willReturn(objectMapper.readTree("[{\"symbol\":\"005930\",\"lastPrice\":\"72000\"}]"));
    }

    @AfterEach
    void tearDown() {
        apiExecutor.destroy();
    }

    private JsonNode holdings(String itemsJson) {
        return objectMapper.readTree("{\"items\":" + itemsJson + "}");
    }

    @Test
    @DisplayName("보유 종목이면 보유수량과 평균단가를 채운다")
    void fillsHoldingWhenHeld() {
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ, "005930")).willReturn(holdings("""
                [{"symbol":"005930","quantity":"100","averagePurchasePrice":"65000.5"}]
                """));

        TossOrderableResponse response = reader.read(OWNER, ACCOUNT_SEQ, SAMSUNG, "BUY");

        assertThat(response.holdingQuantity()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(response.averagePurchasePrice()).isEqualByComparingTo(new BigDecimal("65000.5"));
    }

    @Test
    @DisplayName("보유하지 않은 종목이면 둘 다 null 이다 — 0 으로 채우면 화면이 평단 0원을 그린다")
    void nullsWhenNotHeld() {
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ, "005930")).willReturn(holdings("[]"));

        TossOrderableResponse response = reader.read(OWNER, ACCOUNT_SEQ, SAMSUNG, "BUY");

        assertThat(response.holdingQuantity()).isNull();
        assertThat(response.averagePurchasePrice()).isNull();
    }

    @Test
    @DisplayName("보유 조회에 실패해도 나머지 값은 살아 있고 보유 값만 null 이다")
    void survivesHoldingFailure() {
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ, "005930")).willThrow(new RuntimeException("toss down"));

        TossOrderableResponse response = reader.read(OWNER, ACCOUNT_SEQ, SAMSUNG, "SELL");

        assertThat(response.lastPrice()).isEqualByComparingTo(new BigDecimal("72000"));
        assertThat(response.holdingQuantity()).isNull();
        assertThat(response.averagePurchasePrice()).isNull();
    }

    @Test
    @DisplayName("매도 화면에서도 보유 값을 채운다 — 예상 손익 계산에 쓴다")
    void fillsHoldingOnSellSide() {
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ, "005930")).willReturn(holdings("""
                [{"symbol":"005930","quantity":"10","averagePurchasePrice":"65000"}]
                """));

        TossOrderableResponse response = reader.read(OWNER, ACCOUNT_SEQ, SAMSUNG, "SELL");

        assertThat(response.holdingQuantity()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(response.averagePurchasePrice()).isEqualByComparingTo(new BigDecimal("65000"));
    }

    @Test
    @DisplayName("매도 비용률 = (수수료 + 세금) / 평가금액 — 토스 추정치를 이 주문 금액에 비례시키기 위한 값")
    void derivesSellCostRateFromTossEstimate() {
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ, "005930")).willReturn(holdings("""
                [{"symbol":"005930","quantity":"100","averagePurchasePrice":"65000",
                  "marketValue":{"amount":"7200000"},
                  "cost":{"commission":"1080","tax":"10800"}}]
                """));

        TossOrderableResponse response = reader.read(OWNER, ACCOUNT_SEQ, SAMSUNG, "SELL");

        // (1,080 + 10,800) / 7,200,000 = 0.00165
        assertThat(response.sellCostRate()).isEqualByComparingTo(new BigDecimal("0.00165"));
    }

    @Test
    @DisplayName("세금이 없는 종목(null)은 수수료만으로 비용률을 만든다")
    void treatsNullTaxAsZero() {
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ, "005930")).willReturn(holdings("""
                [{"symbol":"005930","quantity":"100","averagePurchasePrice":"65000",
                  "marketValue":{"amount":"7200000"},
                  "cost":{"commission":"1080","tax":null}}]
                """));

        TossOrderableResponse response = reader.read(OWNER, ACCOUNT_SEQ, SAMSUNG, "SELL");

        assertThat(response.sellCostRate()).isEqualByComparingTo(new BigDecimal("0.00015"));
    }

    @Test
    @DisplayName("평가금액이 0 이거나 비용이 없으면 비용률은 null 이다 — 0 으로 주면 화면이 비용 없는 매도로 그린다")
    void nullsSellCostRateWhenUnderivable() {
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ, "005930")).willReturn(holdings("""
                [{"symbol":"005930","quantity":"100","averagePurchasePrice":"65000",
                  "marketValue":{"amount":"0"},
                  "cost":{"commission":"1080","tax":"10800"}}]
                """));
        assertThat(reader.read(OWNER, ACCOUNT_SEQ, SAMSUNG, "SELL").sellCostRate()).isNull();

        given(holdingsReader.read(OWNER, ACCOUNT_SEQ, "005930")).willReturn(holdings("""
                [{"symbol":"005930","quantity":"100","averagePurchasePrice":"65000",
                  "marketValue":{"amount":"7200000"}}]
                """));
        assertThat(reader.read(OWNER, ACCOUNT_SEQ, SAMSUNG, "SELL").sellCostRate()).isNull();
    }

    @Test
    @DisplayName("보유하지 않은 종목이면 비용률도 null 이다")
    void nullsSellCostRateWhenNotHeld() {
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ, "005930")).willReturn(holdings("[]"));

        assertThat(reader.read(OWNER, ACCOUNT_SEQ, SAMSUNG, "SELL").sellCostRate()).isNull();
    }

    @Test
    @DisplayName("주문 화면 값 네 가지를 동시에 조회한다")
    void fetchesAllFieldsConcurrently() {
        // 현재가·상하한가·보유·매수가능금액은 서로를 기다릴 이유가 없다. 차례로 부르면 시트가 왕복 4번만큼 늦게 뜬다.
        // 넷이 모두 동시에 떠 있어야 장벽을 넘는다 — 순차 호출이면 첫 호출이 장벽에서 시간 초과로 실패한다.
        CyclicBarrier allInFlight = new CyclicBarrier(4);
        given(tossInvestClient.get(eq(OWNER), eq(PRICES_PATH), any())).willAnswer(invocation -> {
            allInFlight.await(2, TimeUnit.SECONDS);
            return objectMapper.readTree("""
                    [{"symbol":"005930","lastPrice":"72000"}]
                    """);
        });
        given(tossInvestClient.get(eq(OWNER), eq("/api/v1/price-limits"), any())).willAnswer(invocation -> {
            allInFlight.await(2, TimeUnit.SECONDS);
            return objectMapper.readTree("""
                    {"upperLimitPrice":"93600","lowerLimitPrice":"50400"}
                    """);
        });
        given(tossInvestClient.get(eq(OWNER), eq("/api/v1/buying-power"), any(), eq(ACCOUNT_SEQ))).willAnswer(invocation -> {
            allInFlight.await(2, TimeUnit.SECONDS);
            return objectMapper.readTree("""
                    {"cashBuyingPower":"1000000"}
                    """);
        });
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ, "005930")).willAnswer(invocation -> {
            allInFlight.await(2, TimeUnit.SECONDS);
            return holdings("[]");
        });

        TossOrderableResponse response = reader.read(OWNER, ACCOUNT_SEQ, SAMSUNG, "BUY");

        // 각 조회는 실패하면 null 로 삼켜지므로, 넷 다 값이 있어야 넷 다 장벽을 넘은 것이다.
        assertThat(response.lastPrice()).isEqualByComparingTo("72000");
        assertThat(response.upperLimitPrice()).isEqualByComparingTo("93600");
        assertThat(response.cashBuyingPower()).isEqualByComparingTo("1000000");
        assertThat(response.holdingQuantity()).isNull();
    }

    @Test
    @DisplayName("종목 시장에 적용되는 계좌 수수료율을 채운다")
    void fillsCommissionRateForStockMarket() {
        // 보유 비용 추정치는 보유 종목 매도에만 있다. 수수료율이 있어야 매수·미보유 종목에도 예상 수수료를 낸다.
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ, "005930")).willReturn(holdings("[]"));
        given(commissionReader.read(OWNER, ACCOUNT_SEQ)).willReturn(objectMapper.readTree("""
                [{"marketCountry":"KR","commissionRate":"0.00015","startDate":null,"endDate":null},
                 {"marketCountry":"US","commissionRate":"0.001","startDate":null,"endDate":null}]
                """));

        assertThat(reader.read(OWNER, ACCOUNT_SEQ, SAMSUNG, "BUY").commissionRate()).isEqualByComparingTo("0.00015");
    }

    @Test
    @DisplayName("수수료율 조회가 실패해도 나머지 값은 그대로 두고 수수료율만 비운다")
    void leavesCommissionRateNullWhenLookupFails() {
        given(holdingsReader.read(OWNER, ACCOUNT_SEQ, "005930")).willReturn(holdings("[]"));
        given(commissionReader.read(OWNER, ACCOUNT_SEQ)).willThrow(new RuntimeException("boom"));

        TossOrderableResponse response = reader.read(OWNER, ACCOUNT_SEQ, SAMSUNG, "BUY");

        assertThat(response.commissionRate()).isNull();
        assertThat(response.lastPrice()).isEqualByComparingTo("72000");
    }
}
