package com.behcm.domain.tossstock.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 검색 가능한 전체 종목 목록을 메모리에 들고 있는다.
 *
 * <p>토스에는 종목 검색 API 가 없다. 대신 {@code GET /api/v1/stocks/all} 이 마켓별 전량을 주고
 * 스펙이 "하루 1회 조회 후 로컬 캐싱"을 권한다. 그래서 유니버스를 통째로 받아 여기 둔다.
 *
 * <p><b>Caffeine {@code @Cacheable} 을 쓰지 않는 이유</b>: 그쪽은 "키 하나에 값 하나"를 위한 것이라
 * 전체를 훑는 검색과 맞지 않고, TTL 이 만료되는 순간에 걸린 요청 하나가 5회 외부 호출을 통째로
 * 뒤집어쓴다. 여기서는 적재를 배치가 맡고, 조회는 항상 이미 완성된 인덱스만 본다.
 *
 * <p>갱신은 <b>참조 교체</b>다({@link #replace}). 제자리에서 고치면 재적재 중에 검색이 반쯤 빈
 * 목록을 보게 되지만, 통째로 갈아끼우면 그 순간까지 이전 인덱스가 그대로 서빙된다.
 */
@Slf4j
@Component
public class TossStockUniverse {

    /** 엔트리 목록과 심볼 색인을 한 덩어리로 묶어 원자적으로 교체한다. */
    private record Index(List<TossListedStock> entries, Map<String, TossListedStock> bySymbol) { }

    private static final Index EMPTY = new Index(List.of(), Map.of());

    private volatile Index index = EMPTY;

    /**
     * 아직 한 번도 적재되지 않았는지. 이 상태의 검색은 빈 결과가 아니라 에러여야 한다 —
     * 빈 결과는 "그런 종목이 없다"로 읽히지만 사실은 "아직 못 받았다"이다.
     */
    public boolean isReady() {
        return !index.entries().isEmpty();
    }

    public void replace(List<TossListedStock> entries) {
        Map<String, TossListedStock> bySymbol = new HashMap<>();
        for (TossListedStock entry : entries) {
            // 같은 심볼이 두 마켓에 있으면 먼저 적재된 쪽을 남긴다(TossMarkets.TRADABLE 순서).
            bySymbol.putIfAbsent(entry.normalizedSymbol(), entry);
        }
        this.index = new Index(List.copyOf(entries), Map.copyOf(bySymbol));
        log.debug("Toss stock universe replaced: {} entries", entries.size());
    }

    public List<TossListedStock> entries() {
        return index.entries();
    }

    public Optional<TossListedStock> findBySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(index.bySymbol().get(symbol.trim().toUpperCase(java.util.Locale.ROOT)));
    }
}
