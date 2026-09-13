package com.behcm.global.config;

import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorLogLevel;
import com.behcm.global.logging.MdcTaskDecorator;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;
import java.util.concurrent.Executor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    @Bean(name = "mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);   // 동시에 처리할 기본 스레드 수
        executor.setMaxPoolSize(5);    // 최대 스레드 수
        executor.setQueueCapacity(10); // 대기 큐 크기
        executor.setThreadNamePrefix("Mail-");
        // 발송 로그에 요청의 requestId/memberId 가 붙도록 MDC 를 옮긴다.
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * {@code @Async void} 메서드에서 새어 나온 예외는 호출자에게 전달되지 않는다. 기본 핸들러는 전부
     * "Unexpected exception occurred invoking async method" ERROR 로 찍어, 이미 가입된 이메일로
     * 인증 메일을 보내려던 것(정상 흐름)까지 ERROR 가 된다. HTTP 와 같은 기준으로 레벨을 나눈다.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            String where = method.getDeclaringClass().getSimpleName() + "." + method.getName();
            if (ex instanceof CustomException custom) {
                Level level = ErrorLogLevel.of(custom.getErrorCode());
                var event = log.atLevel(level);
                if (level == Level.ERROR) {
                    event = event.setCause(ex);
                }
                event.log("[{}] async {} failed - {}", custom.getErrorCode(), where, ex.getMessage());
                return;
            }
            // 파라미터는 타입만 남긴다 — 값에 이메일·토큰이 섞여 있을 수 있다.
            log.error("[UNHANDLED] async {} failed (paramTypes={}) - {}: {}",
                    where, Arrays.stream(params).map(p -> p == null ? "null" : p.getClass().getSimpleName()).toList(),
                    ex.getClass().getName(), ex.getMessage(), ex);
        };
    }
}
