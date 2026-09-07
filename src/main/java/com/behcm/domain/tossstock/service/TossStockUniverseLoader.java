package com.behcm.domain.tossstock.service;

import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestClient;
import com.behcm.global.config.toss.TossInvestProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 검색 유니버스를 토스에서 받아 {@link TossStockUniverse} 에 채운다.
 *
 * <p><b>왜 배치인가</b>: 첫 검색에서 lazy 로 받으면 그 사람 혼자 5회 순차 호출(체감 3~6초)을
 * 뒤집어쓴다. 스펙도 "일 배치로 갱신되는 저변동 데이터이므로 하루 1회 조회 후 로컬 캐싱"을 권한다.
 * 그래서 부팅 직후 한 번, 이후 매일 장 시작 전에 한 번 받아 둔다.
 *
 * <p>호출 사이에 인위적인 sleep 을 두지 않는다. {@code STOCK_ALL} 한도에 걸리면
 * {@link TossInvestClient} 의 429 백오프가 이미 처리한다 — 같은 일을 하는 장치를 둘 두지 않는다.
 *
 * <p>실패는 조용히 삼킨다. 여기서 예외가 새면 {@code @Async} 스레드에는 스택트레이스만 남고,
 * {@code @Scheduled} 는 그 다음 실행이 막혀 유니버스가 영영 갱신되지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossStockUniverseLoader {

    private final TossInvestClient tossInvestClient;
    private final TossInvestProperties properties;
    private final TossStockUniverse universe;

    private static final String STOCKS_ALL_PATH = "/api/v1/stocks/all";

    /**
     * 부팅 직후 워밍업. 부팅 자체를 막지 않도록 비동기로 돈다
     * ({@code AsyncConfig} 에 {@code @EnableAsync} 가 이미 있다).
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpOnStartup() {
        reloadQuietly();
    }

    /**
     * 일 1회 갱신. 토스의 일 배치가 끝난 뒤이면서 국내 장 시작(09:00)보다는 앞이어야 한다.
     */
    @Scheduled(cron = "0 30 6 * * *", zone = "Asia/Seoul")
    public void refreshDaily() {
        reloadQuietly();
    }

    private void reloadQuietly() {
        try {
            reload();
        } catch (Exception e) {
            log.error("Failed to refresh the Toss stock universe; keeping the previous index", e);
        }
    }

    /**
     * 마켓별로 전량을 받아 인덱스를 새로 만든다.
     *
     * <p>한 마켓이 실패해도 나머지로 만든다 — 코스닥이 안 되는 날에도 코스피 종목은 사고팔 수 있어야 한다.
     * 전부 실패하면 <b>교체하지 않는다</b>. 어제 목록이 없는 목록보다 낫다.
     */
    void reload() {
        // /stocks/all 은 계좌 컨텍스트가 필요 없지만 토큰은 소유자별이다. 아무나 한 명이면 되고,
        // 결과(시장 데이터)는 소유자와 무관하므로 전원이 같은 인덱스를 공유한다.
        List<TossAccountOwner> owners = properties.configuredOwners();
        if (owners.isEmpty()) {
            log.info("No Toss account is configured; skipping the stock universe load");
            return;
        }
        TossAccountOwner owner = owners.get(0);

        List<TossListedStock> collected = new ArrayList<>();
        int failedMarkets = 0;

        for (String market : TossMarkets.TRADABLE) {
            try {
                JsonNode listed = tossInvestClient.get(
                        owner, STOCKS_ALL_PATH, Map.of("market", market, "status", "ACTIVE"));
                collected.addAll(toEntries(listed, market));
            } catch (Exception e) {
                failedMarkets++;
                log.warn("Failed to load the Toss stock universe for market={}", market, e);
            }
        }

        if (collected.isEmpty()) {
            log.error("Toss stock universe load produced no entries ({} markets failed); keeping the previous index",
                    failedMarkets);
            return;
        }

        universe.replace(collected);
    }

    private List<TossListedStock> toEntries(JsonNode listed, String market) {
        if (!listed.isArray()) {
            return List.of();
        }
        List<TossListedStock> entries = new ArrayList<>();
        for (JsonNode stock : listed) {
            String symbol = stock.path("symbol").asString("");
            String name = stock.path("name").asString("");
            // 심볼이 없으면 주문할 수 없고, 이름이 없으면 검색으로 찾을 수 없다. 둘 다 없으면 안 된다.
            if (symbol.isBlank() || name.isBlank()) {
                continue;
            }
            entries.add(TossListedStock.of(
                    symbol,
                    name,
                    market,
                    stock.path("securityType").asString(""),
                    stock.path("isCommonShare").asBoolean(true)
            ));
        }
        return entries;
    }
}
