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

    /** 세금/수수료 공제 후 평가금액·손익. 토스가 요약 레벨에도 내려주는 값이다. */
    private BigDecimal totalMarketValueAfterCostKrw;
    private BigDecimal totalMarketValueAfterCostUsd;
    private BigDecimal totalProfitLossAfterCostKrw;
    private BigDecimal totalProfitLossAfterCostUsd;
    /** 세금/수수료 공제 후 전체 손익률(%). 위 손익률과 마찬가지로 원화 환산 기준이다. */
    private BigDecimal totalProfitLossRateAfterCost;

    /**
     * 적용 환율(1 USD = ? KRW). 해외 종목이 없거나 환율 조회에 실패하면 null.
     * 화면에 환산 금액을 보여주는 이상 어떤 환율을 썼는지도 같이 보여줘야 한다.
     */
    private BigDecimal usdKrwRate;
    /** 매매기준율(은행간 mid rate). */
    private BigDecimal usdKrwMidRate;
    /** 환율 등락 구분. UP | EQUAL | DOWN. */
    private String usdKrwRateChangeType;
    /** 이 환율의 유효 시작 시각. */
    private String usdKrwRateAsOf;

    /**
     * 국내 금액 + 해외 금액×환율. 통화별로 나뉜 위 합계와 달리 <b>계좌 전체</b>를 가리킨다.
     *
     * <p>해외 종목이 없으면 환산할 것이 없으므로 국내 금액이 그대로 들어간다.
     * 해외 종목이 있는데 환율 조회에 실패하면 <b>전부 null</b> 이다 — 0 으로 채우면
     * 해외 자산이 통째로 사라진 것처럼 보인다.
     */
    private BigDecimal totalPurchaseAmountInKrw;
    private BigDecimal totalMarketValueInKrw;
    private BigDecimal totalProfitLossInKrw;
    private BigDecimal totalProfitLossAfterCostInKrw;
    private BigDecimal dailyProfitLossInKrw;
    /** 원화 환산 평가금액에서 해외가 차지하는 비중(%). */
    private BigDecimal overseasWeightPercent;

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
