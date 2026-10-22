package com.behcm.domain.tossstock.service;

import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 심볼 → 종목명 매핑.
 *
 * <p>토스의 주문 응답({@code /api/v1/orders})에는 종목명이 없어 별도로 채워야 하는데,
 * 그 사정이 주문내역(실현손익)과 미체결 목록 양쪽에 똑같이 있다. 두 번 구현하면
 * 200건 배치 상한이나 실패 처리 규칙이 한쪽만 바뀌는 일이 생기므로 여기 한 곳에 둔다.
 *
 * <p><b>유니버스를 먼저 본다.</b> 검색용으로 메모리에 들고 있는 {@link TossStockUniverse} 가 거래 가능한
 * 종목의 이름을 이미 알고 있어, 미체결 목록을 열 때마다·주문내역을 전체 동기화할 때마다 나가던
 * {@code /stocks} 호출이 대부분 사라진다. 유니버스에 없는 심볼(상장폐지 종목, 부팅 직후 아직 못 받은 경우)만
 * 토스에 묻는다. 그래서 static 헬퍼였던 것을 유니버스를 쥔 빈으로 바꿨다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TossStockNameResolver {

    private final TossInvestClient tossInvestClient;
    private final TossStockUniverse universe;

    private static final String STOCKS_PATH = "/api/v1/stocks";

    /** 종목 기본정보 조회의 심볼 개수 상한. */
    private static final int SYMBOL_BATCH_SIZE = 200;

    /**
     * 심볼 목록의 종목명을 채운다.
     *
     * <p>이름은 <b>표시용 부가정보</b>다. 조회가 실패해도 호출자의 본 데이터(체결·미체결)는 그대로
     * 유효하므로 예외를 밖으로 내보내지 않는다 — 빠진 심볼은 호출자가 심볼 자체로 대체하면 된다.
     *
     * @return 심볼 → 종목명. 조회하지 못한 심볼은 키 자체가 없다.
     */
    Map<String, String> resolve(TossAccountOwner owner, Collection<String> symbols) {
        Set<String> distinct = new LinkedHashSet<>();
        for (String symbol : symbols) {
            if (symbol != null && !symbol.isBlank()) {
                distinct.add(symbol);
            }
        }
        if (distinct.isEmpty()) {
            return Map.of();
        }

        // 검색용으로 이미 들고 있는 유니버스를 먼저 본다. 거래 가능한(ACTIVE) 종목은 대부분 여기서 끝난다.
        Map<String, String> names = new HashMap<>();
        List<String> batch = new ArrayList<>();
        for (String symbol : distinct) {
            universe.findBySymbol(symbol).ifPresentOrElse(
                    stock -> names.put(symbol, stock.name()),
                    () -> batch.add(symbol));
        }

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
}
