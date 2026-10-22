package com.behcm.domain.tossstock.dto;

/**
 * 정정 결과. {@code orderId} 는 정정으로 <b>새로 발급된</b> 주문번호다 — 원주문 번호와 다르고,
 * 이후 취소·재정정은 이 번호로 해야 한다.
 */
public record TossOrderModifyResponse(String orderId) { }
