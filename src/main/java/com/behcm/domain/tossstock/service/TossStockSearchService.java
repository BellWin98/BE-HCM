package com.behcm.domain.tossstock.service;

import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 종목 검색.
 *
 * <p>토스 Open API 에는 검색 엔드포인트가 없어서(스펙 전체에 "검색"이라는 말이 없다) 순위를 우리가 매긴다.
 * 외부 호출이 없으므로 타이핑마다 불려도 비용이 거의 들지 않는다 — 검색 결과에 시세를 붙이지 않는 것도
 * 같은 이유다. 시세는 종목을 고른 뒤 주문 화면이 한 번만 받아 온다.
 *
 * <p>랭킹은 "무엇을 치면 무엇이 나와야 하는가"로 정했다:
 * <ol>
 *   <li>심볼을 정확히 안다면 그건 그 종목을 지목한 것이다 → 최상단</li>
 *   <li>이름의 <b>시작</b>이 맞는 쪽이 중간에 걸린 쪽보다 의도에 가깝다</li>
 *   <li>같은 점수라면 보통주 → 짧은 이름 순. `삼성전자` 가 `삼성전자우`·`삼성전자서비스` 보다 먼저 와야 한다</li>
 * </ol>
 *
 * <p>한글 초성 검색은 넣지 않았다. 별도 색인이 필요한 데 비해 "ㅅㅅㅈㅈ" 같은 질의가 수백 건과
 * 맞아떨어져 정확도가 떨어지고, 이 화면을 쓰는 사람은 사실상 한 명이다.
 */
@Service
@RequiredArgsConstructor
public class TossStockSearchService {

    private final TossStockUniverse universe;

    /** 점수는 낮을수록 위. 값 자체에 의미는 없고 순서만 의미가 있다. */
    private static final int SCORE_SYMBOL_EXACT = 0;
    private static final int SCORE_SYMBOL_PREFIX = 1;
    private static final int SCORE_NAME_PREFIX = 2;
    private static final int SCORE_NAME_CONTAINS = 3;
    private static final int SCORE_SYMBOL_CONTAINS = 4;
    private static final int NO_MATCH = Integer.MAX_VALUE;

    public List<TossListedStock> search(String query, int limit) {
        String normalized = TossListedStock.normalize(query);
        if (normalized.isEmpty()) {
            return List.of();
        }
        if (!universe.isReady()) {
            // 빈 목록을 주면 화면이 "그런 종목 없음"으로 읽는다. 아직 못 받은 것과는 다른 사실이다.
            throw new CustomException(ErrorCode.TOSS_STOCK_UNIVERSE_NOT_READY);
        }

        String symbolQuery = normalized.toUpperCase(Locale.ROOT);

        List<Scored> matched = new ArrayList<>();
        for (TossListedStock entry : universe.entries()) {
            int score = score(entry, normalized, symbolQuery);
            if (score != NO_MATCH) {
                matched.add(new Scored(entry, score));
            }
        }

        matched.sort(Comparator
                .comparingInt(Scored::score)
                // 같은 점수 안에서의 순서 — 사려는 쪽이 대개 보통주이고, 이름이 짧은 쪽이 본주다.
                .thenComparing((Scored scored) -> scored.entry().commonShare() ? 0 : 1)
                .thenComparingInt(scored -> scored.entry().name().length())
                .thenComparing(scored -> scored.entry().symbol()));

        int capped = Math.max(0, Math.min(limit, matched.size()));
        return matched.subList(0, capped).stream().map(Scored::entry).toList();
    }

    private int score(TossListedStock entry, String nameQuery, String symbolQuery) {
        String symbol = entry.normalizedSymbol();
        if (symbol.equals(symbolQuery)) {
            return SCORE_SYMBOL_EXACT;
        }
        if (symbol.startsWith(symbolQuery)) {
            return SCORE_SYMBOL_PREFIX;
        }

        String name = entry.normalizedName();
        if (name.startsWith(nameQuery)) {
            return SCORE_NAME_PREFIX;
        }
        if (name.contains(nameQuery)) {
            return SCORE_NAME_CONTAINS;
        }
        if (symbol.contains(symbolQuery)) {
            return SCORE_SYMBOL_CONTAINS;
        }
        return NO_MATCH;
    }

    private record Scored(TossListedStock entry, int score) { }
}
