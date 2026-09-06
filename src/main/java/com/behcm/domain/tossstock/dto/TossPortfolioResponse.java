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
 *   <li>국내(KRW)·미국(USD) 종목이 섞이므로 합산 금액이 통화별로 분리된다. 해외 종목이 없으면 usd 는 null.
 *       통화를 넘어 합산하지 않는다 — 환율로 환산하면 매수 시점과 다른 값이 되기 때문이다.</li>
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
    /**
     * 전체 손익률(%). 토스가 <b>국내·해외를 원화 환산해 합친</b> 기준으로 계산해 준 값이다.
     * 위 통화별 금액들과 모집단이 다르므로 한쪽 통화만 보는 화면에 붙이면 안 된다
     * (금액은 마이너스인데 비율은 플러스인 줄이 나온다).
     */
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
    /** 세금/수수료 공제 후 전체 손익률(%). 위 손익률과 마찬가지로 원화 환산 합산 기준이다. */
    private BigDecimal totalProfitLossRateAfterCost;

    /**
     * 적용 환율(1 USD = ? KRW). 해외 종목이 없거나 환율 조회에 실패하면 null.
     *
     * <p>금액 환산에는 <b>쓰지 않는다</b> — 평단·매입금액·손익은 매수/매도 시점 환율로 확정된
     * 과거 금액이라 오늘 환율을 곱하면 실제로 치른 원화도, 앞으로 손에 쥘 원화도 아닌 값이 된다.
     * 참고 표기("$1 = ₩1,382.4")로만 내려준다.
     */
    private BigDecimal usdKrwRate;
    /** 매매기준율(은행간 mid rate). */
    private BigDecimal usdKrwMidRate;
    /** 환율 등락 구분. UP | EQUAL | DOWN. */
    private String usdKrwRateChangeType;
    /** 이 환율의 유효 시작 시각. */
    private String usdKrwRateAsOf;

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
