package com.behcm.domain.tossstock.service;

import java.util.Locale;

/**
 * 검색 유니버스의 종목 한 건.
 *
 * <p>{@code marketCountry}/{@code currency}/{@code locSupported} 는 응답에 없는 파생값이라
 * 적재 시점에 {@link TossMarkets} 로 붙인다 — 화면이 LOC 를 그릴지, 금액을 원화로 쓸지가 여기서 갈린다.
 *
 * <p>{@code normalizedName}/{@code normalizedSymbol} 은 검색용 사전 계산이다. 질의마다 수천 건에
 * {@code toLowerCase}·공백 제거를 다시 돌리면 그 비용이 전부 검색 지연으로 나타난다.
 */
public record TossListedStock(
        String symbol,
        String name,
        String market,
        String securityType,
        boolean commonShare,
        String marketCountry,
        String currency,
        boolean locSupported,
        String normalizedName,
        String normalizedSymbol
) {

    /**
     * 마켓에서 파생값을 채워 엔트리를 만든다. 유니버스 적재와 테스트가 같은 규칙을 쓰도록 이 경로 하나만 둔다.
     */
    public static TossListedStock of(
            String symbol, String name, String market, String securityType, boolean commonShare) {
        return new TossListedStock(
                symbol,
                name,
                market,
                securityType,
                commonShare,
                TossMarkets.countryOf(market),
                TossMarkets.currencyOf(market),
                TossMarkets.supportsLoc(market),
                normalize(name),
                symbol == null ? "" : symbol.toUpperCase(Locale.ROOT)
        );
    }

    /** 검색 비교용 정규화: 소문자 + 공백 제거. 한글은 그대로 둔다. */
    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
