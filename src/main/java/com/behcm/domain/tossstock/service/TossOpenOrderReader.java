package com.behcm.domain.tossstock.service;

import com.behcm.domain.tossstock.dto.TossOpenOrderResponse;
import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 체결을 기다리는 주문 목록.
 *
 * <p><b>캐시하지 않는다.</b> 이 목록은 신선함이 존재 이유다 — 방금 낸 주문이 안 보이거나
 * 이미 체결된 주문이 남아 보이면, 사용자는 같은 주문을 한 번 더 낸다.
 *
 * <p>{@code status=OPEN} 은 스펙상 페이징이 없다(전량 반환). 그래서 주문내역 조회와 달리
 * 커서를 돌 필요가 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossOpenOrderReader {

    private final TossInvestClient tossInvestClient;

    private static final String ORDERS_PATH = "/api/v1/orders";

    /** 취소 버튼을 그릴 수 있는 상태. 이미 취소·정정 요청이 나간 주문은 다시 손대지 않는다. */
    private static final Set<String> CANCELABLE = Set.of("PENDING", "PARTIAL_FILLED");

    public List<TossOpenOrderResponse> read(TossAccountOwner owner, Long accountSeq) {
        JsonNode result = tossInvestClient.get(
                owner, ORDERS_PATH, Map.of("status", "OPEN"), accountSeq);

        JsonNode orders = result.path("orders");
        if (!orders.isArray() || orders.isEmpty()) {
            return List.of();
        }

        // 주문 응답에는 종목명이 없다. 실현손익 쪽과 같은 사정이라 배치 규칙을 공유한다.
        Set<String> symbols = new LinkedHashSet<>();
        for (JsonNode order : orders) {
            symbols.add(order.path("symbol").asString(""));
        }
        Map<String, String> names = TossStockNameResolver.resolve(tossInvestClient, owner, symbols);

        List<TossOpenOrderResponse> responses = new ArrayList<>();
        for (JsonNode order : orders) {
            responses.add(toResponse(order, names));
        }
        return responses;
    }

    private TossOpenOrderResponse toResponse(JsonNode order, Map<String, String> names) {
        String symbol = order.path("symbol").asString("");
        String orderType = order.path("orderType").asString("");
        String timeInForce = order.path("timeInForce").asString("DAY");
        String status = order.path("status").asString("");

        BigDecimal quantity = TossJsonSupport.decimal(order, "quantity");
        BigDecimal filledQuantity = TossJsonSupport.decimal(order.path("execution"), "filledQuantity");

        return TossOpenOrderResponse.builder()
                .orderId(order.path("orderId").asString(""))
                .symbol(symbol)
                // 이름 조회가 실패해도 목록 자체는 유효하다. 심볼로 대체하고 계속 보여준다.
                .name(names.getOrDefault(symbol, symbol))
                .side(order.path("side").asString(""))
                .orderType(orderType)
                .timeInForce(timeInForce)
                // LOC 은 별도 필드가 아니라 이 조합이다. 화면이 매번 다시 판정하지 않도록 여기서 풀어 준다.
                .loc("LIMIT".equals(orderType) && "CLS".equals(timeInForce))
                .status(status)
                .currency(order.path("currency").asString("KRW"))
                .price(TossJsonSupport.nullableDecimal(order, "price"))
                .quantity(quantity)
                .filledQuantity(filledQuantity)
                .remainingQuantity(quantity.subtract(filledQuantity).max(BigDecimal.ZERO))
                .orderedAt(order.path("orderedAt").asString(""))
                .cancelable(CANCELABLE.contains(status))
                .build();
    }
}
