package com.behcm.domain.tossstock.service;

import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestClient;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * USD → KRW 환율을 읽어 짧게 캐시한다.
 *
 * <p>국내·해외가 섞인 계좌는 통화별 합계를 그대로 보여주면 두 숫자를 견줄 수 없다
 * (토스는 {@code krw} 에 국내 종목만, {@code usd} 에 해외 종목만 담아 주고 통화 간 합산은 하지 않는다).
 * 총자산 하나로 합치려면 환율이 필요하다.
 *
 * <p>이 시세는 <b>계좌와 무관</b>해서 소유자가 달라도 값이 같다 — 토큰 발급에만 owner 가 필요하다.
 * 그래서 소유자별이 아니라 통화쌍 하나로 캐시한다. 토스는 1분 주기로 갱신하므로 캐시도 그보다 짧게 잡는다.
 * Rate Limits Group 이 {@code MARKET_INFO} 라 보유주식({@code ASSET}) 한도와도 겹치지 않는다.
 *
 * <p>캐시를 서비스가 아니라 이 컴포넌트에 두는 이유는 {@link TossHoldingsReader} 와 같다 —
 * 같은 빈 안에서 호출하면 Spring 캐시 프록시를 타지 않는다.
 */
@Component
@RequiredArgsConstructor
public class TossExchangeRateReader {

    private final TossInvestClient tossInvestClient;

    private static final String EXCHANGE_RATE_PATH = "/api/v1/exchange-rate";

    @Cacheable(value = "tossExchangeRate", key = "'USD-KRW'")
    public JsonNode readUsdToKrw(TossAccountOwner owner) {
        return tossInvestClient.get(owner, EXCHANGE_RATE_PATH,
                Map.of("baseCurrency", "USD", "quoteCurrency", "KRW"));
    }
}
