package com.behcm.domain.tossstock.service;

import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 토스 조회를 병렬로 보내는 실행기.
 *
 * <p>호출자 입장에서는 순차 호출과 똑같이 보여야 한다 — 같은 예외가 같은 타입으로 나오고,
 * 같은 요청의 로그(requestId/memberId)가 붙어야 한다.
 */
class TossApiExecutorTest {

    private final TossApiExecutor executor = new TossApiExecutor();

    @AfterEach
    void tearDown() {
        executor.destroy();
        MDC.clear();
    }

    @Test
    @DisplayName("작업 결과를 그대로 돌려준다")
    void returnsTaskResult() {
        CompletableFuture<String> future = executor.supply(() -> "ok");

        assertThat(TossApiExecutor.join(future)).isEqualTo("ok");
    }

    @Test
    @DisplayName("작업에서 난 CustomException 은 감싸지 않고 그대로 던진다")
    void rethrowsCustomExceptionUnwrapped() {
        // 감싼 채로 나가면 GlobalExceptionHandler 가 비즈니스 에러를 500 으로 바꾼다.
        CompletableFuture<String> future = executor.supply(() -> {
            throw new CustomException(ErrorCode.TOSS_API_FAILED);
        });

        assertThatThrownBy(() -> TossApiExecutor.join(future))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOSS_API_FAILED);
    }

    @Test
    @DisplayName("요청 스레드의 MDC 를 작업 스레드로 옮긴다")
    void propagatesMdcToWorker() {
        // 없으면 토스 호출 실패 로그에 memberId 가 빠져 누구의 요청이었는지 잇지 못한다.
        MDC.put("memberId", "42");

        CompletableFuture<String> future = executor.supply(() -> MDC.get("memberId"));

        assertThat(TossApiExecutor.join(future)).isEqualTo("42");
    }
}
