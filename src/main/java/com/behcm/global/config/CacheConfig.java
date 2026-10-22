package com.behcm.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();

        CaffeineCache memberProfileCache = new CaffeineCache(
                "memberProfile",
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .maximumSize(10_000)
                        .build()
        );

        CaffeineCache workoutRoomDetailCache = new CaffeineCache(
                "workoutRoomDetail",
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.MINUTES)
                        .maximumSize(5_000)
                        .build()
        );

        // 한국투자증권 API 는 초당 호출 수 제한이 있고, 포트폴리오 1회 조회에
        // (1 + 보유종목수) 회의 외부 호출이 나간다. 계좌는 하나뿐이라 엔트리도 하나면 충분하다.
        CaffeineCache stockPortfolioCache = new CaffeineCache(
                "stockPortfolio",
                Caffeine.newBuilder()
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .maximumSize(1)
                        .build()
        );

        // 주문내역 스냅샷의 메모리 사본(L1). 원본 사본은 Redis(L2)에 있다(TossOrderHistoryStore) —
        // 여기서 만료돼도 전체 페이징이 아니라 Redis 에서 되살릴 뿐이라, TTL 은 메모리를 오래 붙잡지
        // 않으려는 값이다. 어긋난 스냅샷의 자가 치유는 매일 밤 재적재(TossOrderHistoryRefresher)가 맡는다.
        CaffeineCache tossOrderHistoryCache = new CaffeineCache(
                "tossOrderHistory",
                Caffeine.newBuilder()
                        .expireAfterWrite(6, TimeUnit.HOURS)
                        .maximumSize(10)
                        .build()
        );

        // 환율은 계좌와 무관해 소유자가 달라도 값이 같으므로 통화쌍 하나로 공유한다.
        // 토스가 1분 주기로 갱신하니 그보다 짧게 잡아야 화면 값이 뒤처지지 않는다.
        CaffeineCache tossExchangeRateCache = new CaffeineCache(
                "tossExchangeRate",
                Caffeine.newBuilder()
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .maximumSize(1)
                        .build()
        );

        // 계좌 수수료율. 계좌마다 다르고(우대 이벤트) 적용 기간 단위로만 바뀐다. 주문 시트를 열 때마다
        // 묻지 않도록 소유자별로 길게 둔다 — 요율이 바뀌는 날에도 최대 6시간 뒤에는 새 값을 본다.
        CaffeineCache tossCommissionCache = new CaffeineCache(
                "tossCommission",
                Caffeine.newBuilder()
                        .expireAfterWrite(6, TimeUnit.HOURS)
                        .maximumSize(10)
                        .build()
        );

        cacheManager.setCaches(List.of(
                memberProfileCache,
                workoutRoomDetailCache,
                stockPortfolioCache,
                tossOrderHistoryCache,
                tossExchangeRateCache,
                tossCommissionCache
        ));

        return cacheManager;
    }
}

