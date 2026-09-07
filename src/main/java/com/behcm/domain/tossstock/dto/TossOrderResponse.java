package com.behcm.domain.tossstock.dto;

/**
 * 주문 접수 결과. {@code clientOrderId} 를 되돌려 주어 프론트가 자기 요청과 짝지을 수 있게 한다.
 */
public record TossOrderResponse(String orderId, String clientOrderId) { }
