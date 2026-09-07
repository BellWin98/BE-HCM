package com.behcm.domain.tossstock.dto;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * 미체결 주문 한 건.
 *
 * <p>주문을 낼 수 있게 된 이상 <b>낸 것이 살아 있는지</b> 볼 수단이 반드시 있어야 한다.
 * 캐시하지 않는다 — 이 목록은 신선함이 존재 이유다.
 *
 * @param loc        {@code LIMIT} + {@code CLS} 조합인지. 화면에 배지로 그린다.
 * @param cancelable 취소 버튼을 그릴지. 이미 취소 요청이 나간 주문(PENDING_CANCEL)에는 그리지 않는다.
 */
@Builder
public record TossOpenOrderResponse(
        String orderId,
        String symbol,
        /** 주문 응답에는 종목명이 없어 종목 기본정보로 따로 채운다. 실패 시 심볼이 들어간다. */
        String name,
        String side,
        String orderType,
        String timeInForce,
        boolean loc,
        String status,
        String currency,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal filledQuantity,
        BigDecimal remainingQuantity,
        String orderedAt,
        boolean cancelable
) { }
