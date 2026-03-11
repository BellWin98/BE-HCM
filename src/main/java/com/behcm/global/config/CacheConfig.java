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

        cacheManager.setCaches(List.of(
                memberProfileCache,
                workoutRoomDetailCache
        ));

        return cacheManager;
    }
}

