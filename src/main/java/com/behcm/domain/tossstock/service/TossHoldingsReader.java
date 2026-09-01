package com.behcm.domain.tossstock.service;

import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestClient;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * 보유주식 응답을 읽어 짧게 캐시한다.
 *
 * <p>자산 화면과 실현손익 계산(원가 시딩)이 같은 응답을 쓰므로 한 곳에서 캐시한다.
 * 캐시를 서비스가 아니라 이 컴포넌트에 두는 이유는, 같은 빈 안에서 호출하면
 * Spring 캐시 프록시를 타지 않아 캐시가 무시되기 때문이다.
 *
 * <p>한투와 달리 이 응답 하나에 종목별 일간 손익률까지 들어 있어 종목 수만큼의 추가 호출이 필요 없다.
 */
@Component
@RequiredArgsConstructor
public class TossHoldingsReader {

    private final TossInvestClient tossInvestClient;

    private static final String HOLDINGS_PATH = "/api/v1/holdings";

    @Cacheable(value = "tossHoldings", key = "#owner")
    public JsonNode read(TossAccountOwner owner, Long accountSeq) {
        return tossInvestClient.get(owner, HOLDINGS_PATH, Map.of(), accountSeq);
    }
}
