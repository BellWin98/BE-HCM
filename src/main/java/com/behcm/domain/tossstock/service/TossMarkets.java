package com.behcm.domain.tossstock.service;

import java.util.List;
import java.util.Set;

/**
 * 토스 마켓 코드에서 파생되는 값들.
 *
 * <p>{@code GET /api/v1/stocks/all} 의 응답({@code ListedStock})에는 통화도 국가도 없다 —
 * 우리가 <b>어떤 market 을 요청했는지</b>만이 그 정보의 출처다. 그래서 적재 시점에 여기서 붙여 둔다.
 *
 * <p>이 매핑이 곧 화면의 LOC 노출 여부를 정한다: 토스는 종가 주문(CLS)을 미국 지정가에만 허용하므로
 * 국내 종목에는 LOC 선택지를 아예 그리지 않는다.
 */
final class TossMarkets {

    /**
     * 검색 유니버스에 적재할 마켓.
     *
     * <p>{@code KR_ETC}/{@code US_ETC} 는 제외한다 — 일반적인 매매 대상이 아니라 유니버스만 부풀린다.
     */
    static final List<String> TRADABLE = List.of("KOSPI", "KOSDAQ", "NYSE", "NASDAQ", "AMEX");

    private static final Set<String> KOREAN = Set.of("KOSPI", "KOSDAQ", "KR_ETC");

    private TossMarkets() {
    }

    static boolean isKorean(String market) {
        return market != null && KOREAN.contains(market);
    }

    /** {@code KR} 또는 {@code US}. 모르는 마켓은 미국으로 보지 않는다 — LOC 이 잘못 열리면 안 된다. */
    static String countryOf(String market) {
        return isKorean(market) ? "KR" : "US";
    }

    static String currencyOf(String market) {
        return isKorean(market) ? "KRW" : "USD";
    }

    /** LOC(= LIMIT + CLS)은 토스 스펙상 미국 주식 전용이다. */
    static boolean supportsLoc(String market) {
        return !isKorean(market);
    }
}
