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
        BigDecimal sellableQuantity
) { }
