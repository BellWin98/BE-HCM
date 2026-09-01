package com.behcm.domain.tossstock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 기간별 실현손익 응답.
 *
 * <p>토스증권에는 한국투자증권의 {@code inquire-period-trade-profit} 에 해당하는 실현손익 API 가 없다.
 * 주문 체결 내역을 이동평균 원가법으로 직접 계산한 결과다.
 *
 * <p>합계를 통화별로 분리하는 이유: 국내(KRW)와 미국(USD) 체결이 한 계좌에 섞여 있는데
 * 이를 한 숫자로 더하면 조용히 틀린 금액이 화면에 뜬다. 환산하지 않고 통화별로 나눠서 준다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TossRealizedProfitResponse {

    private String owner;
    private String ownerName;
    private String period;

    /** 통화별 합계. 국내 종목만 거래했다면 KRW 항목 하나만 들어온다. */
    private List<CurrencyTotals> totals;

    private int tradeCount;
    private List<TossTradeDto> trades;

    /**
     * 원가를 추정으로 메운 체결이 하나라도 있으면 true.
     * (주문 이력보다 앞서 매수한 종목을 매도한 경우 — 화면에 "추정치" 표시가 필요하다)
     */
    private boolean estimated;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrencyTotals {

        /** KRW | USD */
        private String currency;
        private BigDecimal totalBuyAmount;
        private BigDecimal totalSellAmount;
        private BigDecimal totalProfitLoss;
        /** 실현손익률(%) = 실현손익 / 매도된 물량의 원가. */
        private BigDecimal totalProfitLossRate;
        private BigDecimal totalFee;
        private BigDecimal totalTax;
        private int tradeCount;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TossTradeDto {

        private String symbol;
        private String name;
        /** yyyy-MM-dd */
        private String tradeDate;
        /** BUY | SELL */
        private String tradeType;
        /** KRW | USD */
        private String currency;

        private BigDecimal quantity;
        /** 1주당 실제 체결 평균가 = 체결금액 / 체결수량. */
        private BigDecimal price;
        private BigDecimal amount;

        /** 매수 체결은 0. 실현손익은 매도 시점에만 발생한다. */
        private BigDecimal profitLoss;
        /** 실현손익률(%). 매수 체결은 0. */
        private BigDecimal profitLossRate;

        private BigDecimal fee;
        private BigDecimal tax;

        /** 이 체결의 원가가 추정치인지 여부. */
        private boolean estimated;
    }
}
