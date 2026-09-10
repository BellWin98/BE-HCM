package com.behcm.domain.tossstock.service;

import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * 보유주식 응답을 읽는다. <b>캐시하지 않는다.</b>
 *
 * <p>자산 화면과 실현손익 계산(원가 시딩)이 같은 응답을 쓰므로 읽는 곳은 여기 하나로 모은다.
 *
 * <p>짧은 TTL 캐시를 두지 않는 이유: 이 화면은 폴링하지 않고 사용자가 새로고침을 눌러야 다시
 * 받아온다. 즉 캐시가 히트하는 순간이 곧 "지금 값을 보려고 누른 순간"이라, 눌러도 숫자가 그대로인
 * 고장처럼 보인다. 아낄 호출도 적다 — 한투와 달리 이 응답 하나에 종목별 일간 손익률까지 들어 있어
 * 종목 수만큼의 추가 호출이 없고, 이 경로가 계좌당 내보내는 조회는 사실상 이 한 건이다.
 * (실현손익의 원가 시딩이 쓰는 값은 평균단가라 시세와 무관하지만, 그 경로는 주문내역 전체 페이징이
 * 시간을 지배해 호출 한 번 더 나가는 비용이 묻힌다.)
 */
@Component
@RequiredArgsConstructor
public class TossHoldingsReader {

    private final TossInvestClient tossInvestClient;

    private static final String HOLDINGS_PATH = "/api/v1/holdings";

    public JsonNode read(TossAccountOwner owner, Long accountSeq) {
        return tossInvestClient.get(owner, HOLDINGS_PATH, Map.of(), accountSeq);
    }
}
