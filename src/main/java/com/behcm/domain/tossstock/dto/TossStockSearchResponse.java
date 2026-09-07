package com.behcm.domain.tossstock.dto;

import lombok.Builder;

/**
 * 검색 결과 한 건.
 *
 * <p>현재가는 담지 않는다 — 타이핑마다 시세를 붙이면 검색이 외부 API 지연에 묶이고
 * MARKET_DATA 한도를 검색으로 태운다. 시세는 종목을 고른 뒤 {@code /orderable} 이 한 번에 준다.
 *
 * <p>{@code locSupported} 는 화면이 LOC 선택지를 그릴지 정하는 값이다(토스는 종가 주문을
 * 미국 지정가에만 허용한다).
 */
@Builder
public record TossStockSearchResponse(
        String symbol,
        String name,
        String market,
        String marketCountry,
        String currency,
        String securityType,
        boolean locSupported
) { }
