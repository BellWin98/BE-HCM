package com.behcm.domain.tossstock.service;

import com.behcm.domain.tossstock.service.TossRealizedProfitCalculator.Fill;
import com.behcm.domain.tossstock.service.TossRealizedProfitCalculator.TradeSide;
import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 종료된 주문의 체결 내역을 계좌 전체 기간에 대해 읽어 온다.
 *
 * <p>실현손익을 이동평균 원가법으로 계산하려면 <b>계좌 개설 이후 전체 체결</b>을 시간순으로 재생해야 한다.
 * 조회 기간만 잘라 오면 그 이전에 매수한 물량의 원가를 알 수 없다.
 * 따라서 전체를 한 번 읽어 캐시하고, 기간 필터는 계산 결과에 적용한다.
 * (프론트의 수익분석 탭은 기간을 자주 바꾸는데, 그때마다 전체 페이징을 다시 도는 것을 막는 목적도 있다.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossOrderHistoryReader {

    private final TossInvestClient tossInvestClient;

    private static final String ORDERS_PATH = "/api/v1/orders";
    private static final String STOCKS_PATH = "/api/v1/stocks";

    /** 토스 주문 목록의 페이지 크기 상한. */
    private static final int PAGE_SIZE = 100;
    /** 커서가 끝나지 않는 경우에도 호출이 유한하도록 상한을 둔다. */
    static final int MAX_ORDER_PAGES = 100;
    /** 종목 기본정보 조회의 심볼 개수 상한. */
    private static final int SYMBOL_BATCH_SIZE = 200;

    /**
     * 체결 목록(시간 오름차순)과 종목명 매핑.
     * 주문 응답에는 종목명이 없어 별도로 채운다.
     */
    public record OrderHistory(List<Fill> fills, Map<String, String> names) { }

    @Cacheable(value = "tossOrderHistory", key = "#owner")
    public OrderHistory readAll(TossAccountOwner owner, Long accountSeq) {
        List<TimestampedFill> collected = new ArrayList<>();
        String cursor = null;

        for (int page = 0; page < MAX_ORDER_PAGES; page++) {
            Map<String, String> params = new HashMap<>();
            params.put("status", "CLOSED");
            params.put("limit", String.valueOf(PAGE_SIZE));
            if (cursor != null) {
                params.put("cursor", cursor);
            }

            JsonNode result = tossInvestClient.get(owner, ORDERS_PATH, params, accountSeq);

            JsonNode orders = result.path("orders");
            if (orders.isArray()) {
                for (JsonNode order : orders) {
                    toFill(order).ifPresent(collected::add);
                }
            }

            if (!result.path("hasNext").asBoolean(false)) {
                break;
            }
            cursor = result.path("nextCursor").asString("");
            if (cursor.isBlank()) {
                break;
            }

            if (page == MAX_ORDER_PAGES - 1) {
                log.warn("Toss order history hit the {}-page cap (owner={}); realized profit may be incomplete",
                        MAX_ORDER_PAGES, owner);
            }
        }

        // 이동평균 원가는 순서에 의존한다. API 정렬을 신뢰하지 않고 명시적으로 오름차순 정렬한다.
        collected.sort(Comparator.comparing(TimestampedFill::executedAt));

        List<Fill> fills = collected.stream().map(TimestampedFill::fill).toList();
        return new OrderHistory(fills, resolveNames(owner, fills));
    }

    private record TimestampedFill(LocalDateTime executedAt, Fill fill) { }

    private java.util.Optional<TimestampedFill> toFill(JsonNode order) {
        JsonNode execution = order.path("execution");
        BigDecimal filledQuantity = TossJsonSupport.decimal(execution, "filledQuantity");

        // 취소·거부된 주문도 CLOSED 에 포함된다. 체결이 없으면 손익과 무관하다.
        if (filledQuantity.signum() <= 0) {
            return java.util.Optional.empty();
        }

        String side = order.path("side").asString("");
        TradeSide tradeSide = "SELL".equals(side) ? TradeSide.SELL : "BUY".equals(side) ? TradeSide.BUY : null;
        if (tradeSide == null) {
            log.warn("Skipping Toss order with unknown side: {}", side);
            return java.util.Optional.empty();
        }

        // 체결 시각이 없으면 주문 시각으로 대체한다(순서 결정용).
        LocalDateTime executedAt = parseDateTime(execution.path("filledAt").asString(""));
        if (executedAt == null) {
            executedAt = parseDateTime(order.path("orderedAt").asString(""));
        }
        if (executedAt == null) {
            log.warn("Skipping Toss order without a usable timestamp: symbol={}", order.path("symbol").asString(""));
            return java.util.Optional.empty();
        }

        Fill fill = new Fill(
                order.path("symbol").asString(""),
                order.path("currency").asString("KRW"),
                tradeSide,
                executedAt.toLocalDate(),
                filledQuantity,
                TossJsonSupport.decimal(execution, "filledAmount"),
                TossJsonSupport.decimal(execution, "commission"),
                TossJsonSupport.decimal(execution, "tax")
        );
        return java.util.Optional.of(new TimestampedFill(executedAt, fill));
    }

    /**
     * 주문 응답에는 종목명이 없으므로 종목 기본정보 API 로 한 번에 채운다(최대 200건씩).
     * 종목명 조회가 실패해도 손익 자체는 유효하므로 심볼로 대체하고 계속 진행한다.
     */
    private Map<String, String> resolveNames(TossAccountOwner owner, List<Fill> fills) {
        Set<String> symbols = new LinkedHashSet<>();
        for (Fill fill : fills) {
            if (fill.symbol() != null && !fill.symbol().isBlank()) {
                symbols.add(fill.symbol());
            }
        }
        if (symbols.isEmpty()) {
            return Map.of();
        }

        Map<String, String> names = new HashMap<>();
        List<String> batch = new ArrayList<>(symbols);
        for (int start = 0; start < batch.size(); start += SYMBOL_BATCH_SIZE) {
            List<String> chunk = batch.subList(start, Math.min(start + SYMBOL_BATCH_SIZE, batch.size()));
            try {
                JsonNode stocks = tossInvestClient.get(
                        owner, STOCKS_PATH, Map.of("symbols", String.join(",", chunk)));
                if (stocks.isArray()) {
                    for (JsonNode stock : stocks) {
                        String symbol = stock.path("symbol").asString("");
                        String name = stock.path("name").asString("");
                        if (!symbol.isBlank() && !name.isBlank()) {
                            names.put(symbol, name);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to resolve Toss stock names for {} symbols; falling back to symbols",
                        chunk.size(), e);
            }
        }
        return names;
    }

    /**
     * ISO 8601(KST) 문자열을 파싱한다. 오프셋이 붙은 형태와 붙지 않은 형태를 모두 받는다.
     */
    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return OffsetDateTime.parse(trimmed).toLocalDateTime();
        } catch (Exception ignored) {
            // 오프셋이 없는 형태를 시도한다.
        }
        try {
            return LocalDateTime.parse(trimmed);
        } catch (Exception ignored) {
            // 날짜만 있는 형태를 시도한다.
        }
        try {
            return LocalDate.parse(trimmed).atStartOfDay();
        } catch (Exception e) {
            log.warn("Unparsable Toss timestamp: {}", trimmed);
            return null;
        }
    }

}
