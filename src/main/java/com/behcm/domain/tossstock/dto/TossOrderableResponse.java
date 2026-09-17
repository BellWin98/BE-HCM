package com.behcm.domain.tossstock.dto;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * 주문 화면을 채우는 값 한 벌.
 *
 * <p>토스에서는 시세·상하한가·매수가능금액·매도가능수량이 전부 다른 엔드포인트라, 프론트가 네 번
 * 호출하지 않도록 서버가 모아서 한 번에 준다.
 *
 * <p>실패한 항목은 <b>{@code null} 로 남긴다</b>. 0 으로 채우면 "잔고 없음"·"팔 게 없음"으로 읽혀
 * 사용자가 잘못된 판단을 한다 — 화면은 null 을 "—" 로 그린다.
 * 캐시는 두지 않는다. 주문 직전에 보는 값이라 신선함이 존재 이유다.
 */
@Builder
public record TossOrderableResponse(
        String symbol,
        String name,
        String marketCountry,
        String currency,
        String securityType,
        boolean locSupported,
        BigDecimal lastPrice,
        /** 국내 종목만. 미국은 가격 제한이 없어 토스도 null 을 준다. */
        BigDecimal upperLimitPrice,
        BigDecimal lowerLimitPrice,
        /** 매수 화면에서만 채운다. */
        BigDecimal cashBuyingPower,
        /** 매도 화면에서만 채운다. */
        BigDecimal sellableQuantity,
        /**
         * 현재 보유수량. <b>보유하지 않은 종목이면 null</b> (조회 실패도 null).
         * 매도가능수량과 다르다 — 미체결 매도에 묶인 수량까지 포함한 총량이라 매수 후 평균단가 계산에 쓴다.
         */
        BigDecimal holdingQuantity,
        /** 보유 평균단가. {@link #holdingQuantity} 와 함께 null 이거나 함께 채워진다. */
        BigDecimal averagePurchasePrice,
        /**
         * 매도 비용률(소수비율, 예: 0.00165). 토스가 보유 종목마다 내려주는 "현재가에 전량 매도 시
         * 수수료+세금" 을 평가금액으로 나눈 값이다. 화면은 이 비율을 주문 금액에 곱해 세후 예상 손익을 낸다.
         *
         * <p>세율·수수료율을 우리가 들고 있지 않기 위한 우회다 — 표를 복제하면 제도 개정에 낡는다.
         * 미보유·평가금액 0·비용 누락이면 null. 0 으로 주면 "비용 없는 매도"가 된다.
         */
        BigDecimal sellCostRate
) { }
