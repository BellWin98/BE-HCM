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

        // 토스증권은 소유자(나·엄마·아빠)마다 계좌가 달라 엔트리가 사람 수만큼 필요하다.
        // 보유주식 응답 하나로 자산 화면과 실현손익의 원가 시딩을 모두 처리한다.
        CaffeineCache tossHoldingsCache = new CaffeineCache(
                "tossHoldings",
                Caffeine.newBuilder()
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .maximumSize(10)
                        .build()
        );

        // 실현손익은 계좌 개설 이후 전체 주문을 재생해야 계산되므로 페이징 비용이 크다.
        // 수익분석 탭에서 기간을 바꿀 때마다 전체를 다시 읽지 않도록 조금 길게 잡는다.
        CaffeineCache tossOrderHistoryCache = new CaffeineCache(
                "tossOrderHistory",
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(10)
                        .build()
        );

        cacheManager.setCaches(List.of(
                memberProfileCache,
                workoutRoomDetailCache,
                stockPortfolioCache,
                tossHoldingsCache,
                tossOrderHistoryCache
        ));

        return cacheManager;
    }
}

