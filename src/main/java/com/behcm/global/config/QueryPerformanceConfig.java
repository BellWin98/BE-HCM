package com.behcm.global.config;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.stat.QueryStatistics;
import org.hibernate.stat.Statistics;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
@Profile("test")
public class QueryPerformanceConfig {

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    @Bean
    public HealthIndicator databasePerformanceHealthIndicator() {
        return new DatabasePerformanceHealthIndicator();
    }

    @Bean
    public QueryPerformanceMonitor queryPerformanceMonitor() {
        return new QueryPerformanceMonitor(entityManagerFactory);
    }

    public static class DatabasePerformanceHealthIndicator implements HealthIndicator {
        @Override
        public Health health() {
            try {
                return Health.up()
                    .withDetail("status", "Database performance monitoring active")
                    .withDetail("note", "Check logs for query performance metrics")
                    .build();
            } catch (Exception e) {
                return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
            }
        }
    }

    @Slf4j
    public static class QueryPerformanceMonitor {
        private final EntityManagerFactory entityManagerFactory;
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        public QueryPerformanceMonitor(EntityManagerFactory entityManagerFactory) {
            this.entityManagerFactory = entityManagerFactory;
            startPerformanceMonitoring();
        }

        private void startPerformanceMonitoring() {
            SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
            Statistics statistics = sessionFactory.getStatistics();
            statistics.setStatisticsEnabled(true);

            scheduler.scheduleAtFixedRate(() -> {
                logPerformanceMetrics(statistics);
            }, 60, 60, TimeUnit.SECONDS); // 1분마다 성능 메트릭 로깅
        }

        private void logPerformanceMetrics(Statistics statistics) {
            log.info("=== Database Performance Metrics ===");
            log.info("Query execution count: {}", statistics.getQueryExecutionCount());
            log.info("Query execution max time: {} ms", statistics.getQueryExecutionMaxTime());

            double avgQueryTime = statistics.getQueryExecutionCount() > 0
                    ? (double) statistics.getQueryExecutionMaxTime() / statistics.getQueryExecutionCount()
                    : 0;
            log.info("Query execution average time: {} ms", String.format("%.2f", avgQueryTime));

            log.info("Slow query threshold exceeded count: {}", statistics.getSlowQueries());

            // 가장 느린 쿼리 로깅
            String slowestQuery = statistics.getQueryExecutionMaxTimeQueryString();
            if (slowestQuery != null) {
                log.warn("Slowest query: {}", slowestQuery);
            }

            // 캐시 히트율
            long cacheHits = statistics.getSecondLevelCacheHitCount();
            long cacheMisses = statistics.getSecondLevelCacheMissCount();
            double hitRatio = (cacheHits + cacheMisses) > 0 ? (cacheHits * 100.0) / (cacheHits + cacheMisses) : 0.0;
            log.info("Second level cache hit ratio: {}%", String.format("%.2f", hitRatio));

            // 자주 사용되는 쿼리 분석
            String[] queries = statistics.getQueries();
            for (String query : queries) {
                QueryStatistics queryStats = statistics.getQueryStatistics(query);
                if (queryStats != null && queryStats.getExecutionCount() > 10) { // 자주 실행되는 쿼리만
                    log.info("Frequent query [{}]: executions={}, avg_time={} ms",
                            query.substring(0, Math.min(50, query.length())),
                            queryStats.getExecutionCount(),
                            String.format("%.2f", queryStats.getExecutionAvgTime()));
                }
            }
        }
    }
}