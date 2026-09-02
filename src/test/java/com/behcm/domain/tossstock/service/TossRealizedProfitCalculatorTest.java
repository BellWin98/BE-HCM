package com.behcm.domain.tossstock.service;

import com.behcm.domain.tossstock.service.TossRealizedProfitCalculator.Fill;
import com.behcm.domain.tossstock.service.TossRealizedProfitCalculator.RealizedFill;
import com.behcm.domain.tossstock.service.TossRealizedProfitCalculator.TradeSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TossRealizedProfitCalculatorTest {

    private final TossRealizedProfitCalculator calculator = new TossRealizedProfitCalculator();

    private Fill buy(String symbol, String date, String qty, String amount, String commission) {
        return new Fill(symbol, "KRW", TradeSide.BUY, LocalDate.parse(date),
                new BigDecimal(qty), new BigDecimal(amount), new BigDecimal(commission), BigDecimal.ZERO);
    }

    private Fill sell(String symbol, String date, String qty, String amount, String commission, String tax) {
        return new Fill(symbol, "KRW", TradeSide.SELL, LocalDate.parse(date),
                new BigDecimal(qty), new BigDecimal(amount), new BigDecimal(commission), new BigDecimal(tax));
    }

    @Test
    @DisplayName("매수 체결은 실현손익이 0이다")
    void calculate_buyFill_hasNoRealizedProfit() {
        List<RealizedFill> results = calculator.calculate(
                List.of(buy("005930", "2026-01-02", "10", "10000", "15")),
                Map.of()
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).profitLoss()).isEqualByComparingTo("0");
        assertThat(results.get(0).profitLossRate()).isEqualByComparingTo("0");
        assertThat(results.get(0).estimated()).isFalse();
    }

    @Test
    @DisplayName("매수 후 전량 매도하면 매수 수수료까지 원가에 반영해 실현손익을 계산한다")
    void calculate_buyThenFullSell_includesBuyCommissionInCostBasis() {
        // 매수 원가 = 10000 + 15 = 10015, 매도 실수령 = 12000 - 18 - 27 = 11955
        List<RealizedFill> results = calculator.calculate(
                List.of(
                        buy("005930", "2026-01-02", "10", "10000", "15"),
                        sell("005930", "2026-01-10", "10", "12000", "18", "27")
                ),
                Map.of()
        );

        RealizedFill sellResult = results.get(1);
        assertThat(sellResult.profitLoss()).isEqualByComparingTo("1940");
        assertThat(sellResult.profitLossRate()).isEqualByComparingTo("19.37");
        assertThat(sellResult.estimated()).isFalse();
    }

    @Test
    @DisplayName("분할 매수 후 일부 매도하면 이동평균 원가로 실현손익을 계산한다")
    void calculate_partialSellAfterMultipleBuys_usesMovingAverageCost() {
        // 10주 @1000 + 10주 @2000 -> 평균 1500. 10주를 18000에 매도 -> 18000 - 15000 = 3000
        List<RealizedFill> results = calculator.calculate(
                List.of(
                        buy("005930", "2026-01-02", "10", "10000", "0"),
                        buy("005930", "2026-01-03", "10", "20000", "0"),
                        sell("005930", "2026-01-10", "10", "18000", "0", "0")
                ),
                Map.of()
        );

        assertThat(results.get(2).profitLoss()).isEqualByComparingTo("3000");
        assertThat(results.get(2).profitLossRate()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("전량 매도 후 재매수하면 평균단가가 새로 시작된다")
    void calculate_rebuyAfterFullSell_resetsAverageCost() {
        List<RealizedFill> results = calculator.calculate(
                List.of(
                        buy("005930", "2026-01-02", "10", "10000", "0"),
                        sell("005930", "2026-01-03", "10", "15000", "0", "0"),
                        buy("005930", "2026-01-04", "5", "10000", "0"),
                        sell("005930", "2026-01-05", "5", "10500", "0", "0")
                ),
                Map.of()
        );

        assertThat(results.get(1).profitLoss()).isEqualByComparingTo("5000");
        // 재매수분 원가 10000 이 그대로 적용되어야 한다(이전 평균 1000 이 남아 있으면 안 된다)
        assertThat(results.get(3).profitLoss()).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("종목별로 원가를 독립적으로 추적한다")
    void calculate_multipleSymbols_tracksCostPerSymbol() {
        List<RealizedFill> results = calculator.calculate(
                List.of(
                        buy("005930", "2026-01-02", "10", "10000", "0"),
                        buy("000660", "2026-01-02", "10", "50000", "0"),
                        sell("005930", "2026-01-10", "10", "12000", "0", "0"),
                        sell("000660", "2026-01-10", "10", "45000", "0", "0")
                ),
                Map.of()
        );

        assertThat(results.get(2).profitLoss()).isEqualByComparingTo("2000");
        assertThat(results.get(3).profitLoss()).isEqualByComparingTo("-5000");
    }

    @Test
    @DisplayName("매수 이력이 없는 매도는 보유주식의 평균단가로 원가를 메우고 추정치로 표시한다")
    void calculate_sellWithoutPriorBuy_seedsFromHoldingAveragePrice() {
        List<RealizedFill> results = calculator.calculate(
                List.of(sell("005930", "2026-01-10", "10", "10000", "0", "0")),
                Map.of("005930", new BigDecimal("500"))
        );

        // 원가 5000, 매도 10000 -> 5000
        assertThat(results.get(0).profitLoss()).isEqualByComparingTo("5000");
        assertThat(results.get(0).profitLossRate()).isEqualByComparingTo("100.00");
        assertThat(results.get(0).estimated()).isTrue();
    }

    @Test
    @DisplayName("매수 이력도 평균단가도 없는 매도는 체결가를 원가로 간주해 수수료만 손실로 남긴다")
    void calculate_sellWithoutPriorBuyAndNoSeed_treatsSellPriceAsCost() {
        List<RealizedFill> results = calculator.calculate(
                List.of(sell("005930", "2026-01-10", "10", "10000", "20", "30")),
                Map.of()
        );

        // 원가를 10000 으로 간주 -> 실수령 9950 과의 차이인 수수료/세금만 손실로 남는다
        assertThat(results.get(0).profitLoss()).isEqualByComparingTo("-50");
        assertThat(results.get(0).estimated()).isTrue();
    }

    @Test
    @DisplayName("보유 수량보다 많이 매도하면 부족분만 추정 원가로 메운다")
    void calculate_sellMoreThanTrackedPosition_seedsOnlyTheShortfall() {
        List<RealizedFill> results = calculator.calculate(
                List.of(
                        buy("005930", "2026-01-02", "5", "5000", "0"),
                        sell("005930", "2026-01-10", "10", "20000", "0", "0")
                ),
                Map.of("005930", new BigDecimal("1000"))
        );

        // 추적 원가 5000(5주) + 부족분 5주 @1000 = 5000 -> 총 원가 10000, 매도 20000 -> 10000
        assertThat(results.get(1).profitLoss()).isEqualByComparingTo("10000");
        assertThat(results.get(1).estimated()).isTrue();
    }

    @Test
    @DisplayName("수량이 0인 체결은 0으로 나누지 않고 건너뛴다")
    void calculate_zeroQuantityFill_doesNotDivideByZero() {
        List<RealizedFill> results = calculator.calculate(
                List.of(sell("005930", "2026-01-10", "0", "0", "0", "0")),
                Map.of()
        );

        assertThat(results.get(0).profitLoss()).isEqualByComparingTo("0");
        assertThat(results.get(0).profitLossRate()).isEqualByComparingTo("0");
    }
}
