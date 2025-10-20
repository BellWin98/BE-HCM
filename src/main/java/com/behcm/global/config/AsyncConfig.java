package com.behcm.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);   // 동시에 처리할 기본 스레드 수
        executor.setMaxPoolSize(5);    // 최대 스레드 수
        executor.setQueueCapacity(10); // 대기 큐 크기
        executor.setThreadNamePrefix("Mail-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "penaltyExecutor")
    public Executor penaltyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);   // 벌금 계산용 기본 스레드 수
        executor.setMaxPoolSize(8);    // 최대 스레드 수
        executor.setQueueCapacity(20); // 대기 큐 크기
        executor.setThreadNamePrefix("Penalty-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);   // 알림 처리용 기본 스레드 수
        executor.setMaxPoolSize(10);   // 최대 스레드 수
        executor.setQueueCapacity(50); // 대기 큐 크기
        executor.setThreadNamePrefix("Notification-");
        executor.initialize();
        return executor;
    }
}