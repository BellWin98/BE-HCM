package com.behcm.domain.tossstock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 토스증권 보유자산 응답.
 *
 * <p>한국투자증권 응답과 필드가 다르다:
 * <ul>
 *   <li>손익률은 토스가 소수비율(0.1516)로 주지만, 여기서는 <b>퍼센트(15.16)</b>로 변환해 담는다.
 *       프론트가 기존 한투 화면과 같은 포맷터를 쓸 수 있게 하기 위함이다.</li>
 *   <li>국내(KRW)·미국(USD) 종목이 섞이므로 합산 금액이 통화별로 분리된다. 해외 종목이 없으면 usd 는 null.</li>
 *   <li>D+2 예수금 개념이 없다. 토스가 주는 것은 현금 매수가능금액뿐이다.</li>
 * </ul>
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TossPortfolioResponse {

    private String owner;
    private String ownerName;

    private BigDecimal totalPurchaseAmountKrw;
    private BigDecimal totalPurchaseAmountUsd;
    private BigDecimal totalMarketValueKrw;
    private BigDecimal totalMarketValueUsd;
    private BigDecimal totalProfitLossKrw;
    private BigDecimal totalProfitLossUsd;
    /** 전체 손익률(%). 토스가 원화 환산 기준으로 계산해 준 값. */
    private BigDecimal totalProfitLossRate;

    private BigDecimal dailyProfitLossKrw;
    private BigDecimal dailyProfitLossUsd;
    /** 일간 손익률(%). */
    private BigDecimal dailyProfitLossRate;

    /** 현금 매수가능금액(미수 미발생 기준). 한투의 예수금에 대응하는 가장 가까운 값. */
    private BigDecimal cashBuyingPowerKrw;
    private BigDecimal cashBuyingPowerUsd;

    private List<TossHoldingDto> holdings;
    private String lastUpdated;

    /**
     * 종목별 보유 현황. 금액·단가는 모두 해당 종목의 거래통화({@code currency}) 기준이다.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TossHoldingDto {

        private String symbol;
        private String name;
        /** KR | US */
        private String marketCountry;
        /** KRW | USD */
        private String currency;

        /** 보유 수량. 해외 소수점 매매가 있으므로 정수가 아니다. */
        private BigDecimal quantity;
        private BigDecimal lastPrice;
        private BigDecimal averagePurchasePrice;

        private BigDecimal purchaseAmount;
        private BigDecimal marketValue;
        private BigDecimal marketValueAfterCost;

        private BigDecimal profitLoss;
        private BigDecimal profitLossAfterCost;
        /** 손익률(%). */
        private BigDecimal profitLossRate;
        /** 세금·수수료 공제 후 손익률(%). */
        private BigDecimal profitLossRateAfterCost;

        private BigDecimal dailyProfitLoss;
        /**
         * 일간 손익률(%). 한투에서는 종목마다 현재가 API 를 따로 호출해 채우던 값인데,
         * 토스는 보유주식 응답에 이미 들어 있어 추가 호출이 필요 없다.
         */
        private BigDecimal dailyProfitLossRate;

        private BigDecimal commission;
        private BigDecimal tax;
    }
}
