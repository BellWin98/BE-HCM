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

        // 실현손익은 계좌 개설 이후 전체 주문을 재생해야 계산되므로 페이징 비용이 크다.
        // TossOrderHistoryReader 가 이 스냅샷을 들고, 살아 있는 주문을 기준으로 변할 수 있는 구간만
        // 다시 읽는다. 그래서 여기 TTL 은 정합성 장치가 아니라 알 수 없는 이유로 스냅샷이 어긋났을 때의
        // 자가 치유 안전망일 뿐이라 길게 잡는다. 무제한으로 두지 않는 이유도 그것이다 —
        // 워터마크 이전에 잘못 들어간 값은 다시 읽히지 않아 영구히 고착된다.
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

        cacheManager.setCaches(List.of(
                memberProfileCache,
                workoutRoomDetailCache,
                stockPortfolioCache,
                tossOrderHistoryCache,
                tossExchangeRateCache
        ));

        return cacheManager;
    }
}

