package com.behcm.domain.tossstock.service;

import com.behcm.global.logging.MdcTaskDecorator;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Supplier;

/**
 * 서로 기다릴 이유가 없는 토스 조회를 동시에 보낸다.
 *
 * <p>자산 화면·주문 화면은 토스 엔드포인트 여러 개를 모아 한 응답을 만드는데, 차례로 부르면 화면이
 * 왕복 횟수만큼 늦게 뜬다. 대부분 Rate Limits Group 이 서로 달라({@code ASSET}·{@code MARKET_DATA}·
 * {@code ORDER_INFO}·{@code MARKET_INFO}) 한도 부담 없이 겹칠 수 있다.
 *
 * <p><b>풀을 빈으로 등록하지 않는 이유</b>: {@code TaskExecutor} 빈이 하나 더 생기면 한정자 없는
 * {@code @Async} 의 기본 실행기 판정이 바뀐다(지금은 유일한 {@code mailExecutor} 가 쓰인다).
 * 그래서 이 컴포넌트가 풀을 직접 들고, 컨텍스트 종료 시 직접 닫는다.
 *
 * <p>풀이 꽉 차면 호출 스레드가 직접 실행한다({@code CallerRunsPolicy}) — 거부해서 화면을 죽이느니
 * 순차 호출로 되돌아가는 편이 낫다.
 */
@Component
public class TossApiExecutor implements DisposableBean {

    /** 화면 하나가 동시에 내보내는 호출이 4개 남짓이고 사용자는 가족 몇 명이다. */
    private static final int POOL_SIZE = 8;
    private static final int QUEUE_CAPACITY = 50;

    private final ThreadPoolTaskExecutor pool;

    public TossApiExecutor() {
        pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(POOL_SIZE);
        pool.setMaxPoolSize(POOL_SIZE);
        pool.setQueueCapacity(QUEUE_CAPACITY);
        pool.setThreadNamePrefix("TossApi-");
        pool.setDaemon(true);
        pool.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 토스 호출 로그에 요청의 requestId/memberId 가 붙도록 MDC 를 옮긴다.
        pool.setTaskDecorator(new MdcTaskDecorator());
        pool.initialize();
    }

    public <T> CompletableFuture<T> supply(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, pool);
    }

    /**
     * 결과를 기다린다. 작업에서 난 예외는 {@link CompletionException} 을 벗겨 <b>원래 타입 그대로</b>
     * 던진다 — 순차 호출이었을 때와 같은 예외가 나와야 {@code GlobalExceptionHandler} 가 같은 응답을 만든다.
     */
    public static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    @Override
    public void destroy() {
        pool.shutdown();
    }
}
