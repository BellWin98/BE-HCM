package com.behcm.domain.tossstock.service;

import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 밤 주문내역 스냅샷을 전체 다시 적재한다({@link TossOrderHistoryReader#rebuild}).
 *
 * <p>증분 동기화는 워터마크 이전을 다시 읽지 않으므로, 거기에 잘못 들어간 값은 스스로 고쳐지지 않는다.
 * 예전에는 캐시 만료가 그 자가 치유를 맡았고, 그래서 만료 직후 처음 요청한 사용자가 전체 페이징을 떠안았다.
 * 사용자가 없는 시간에 미리 다시 읽어 그 비용을 요청 경로에서 뺀다.
 *
 * <p>실패는 소유자 단위로 삼킨다 — 한 사람의 계좌 조회가 실패했다고 가족 전체의 재적재가 멈추면 안 되고,
 * {@code @Scheduled} 에서 예외가 새면 스택트레이스만 남는다. 실패한 소유자는 기존 스냅샷을 그대로 쓰고
 * 다음 요청의 증분 동기화로 이어 간다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossOrderHistoryRefresher {

    private final TossInvestProperties properties;
    private final TossAccountResolver accountResolver;
    private final TossOrderHistoryReader orderHistoryReader;

    /**
     * 06:40 KST. 미국 정규장 마감(서머타임 05:00 / 표준시 06:00) 뒤라 그날 체결이 다 들어와 있고,
     * 유니버스 갱신(06:30, {@code TossStockUniverseLoader}) 뒤라 종목명이 유니버스에서 풀리며,
     * NXT 프리마켓(08:00) 전이라 사용자 요청과 겹칠 일이 적다.
     */
    @Scheduled(cron = "0 40 6 * * *", zone = "Asia/Seoul")
    public void refreshNightly() {
        for (TossAccountOwner owner : properties.configuredOwners()) {
            long startedAt = System.nanoTime();
            try {
                orderHistoryReader.rebuild(owner, accountResolver.resolveAccountSeq(owner));
                log.info("Toss order history rebuilt (owner={}, took={}ms)",
                        owner, (System.nanoTime() - startedAt) / 1_000_000L);
            } catch (Exception e) {
                log.error("Failed to rebuild the Toss order history (owner={}); keeping the previous snapshot",
                        owner, e);
            }
        }
    }
}
