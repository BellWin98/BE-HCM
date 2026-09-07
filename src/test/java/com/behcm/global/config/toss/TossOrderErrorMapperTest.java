package com.behcm.global.config.toss;

import com.behcm.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 토스 주문 에러 → 우리 ErrorCode 매핑.
 *
 * <p>조회 경로(GET)는 실패 원인이 사실상 인증·한도·장애 셋뿐이라 상태코드만 봐도 됐지만,
 * 주문은 "왜 거부됐는지"가 곧 사용자가 다음에 할 행동을 정한다 — 잔고를 채울지, 시간을 기다릴지,
 * 가격을 고칠지가 전부 다르다. 그래서 상태코드가 아니라 {@code error.code} 로 갈라야 한다.
 */
class TossOrderErrorMapperTest {

    private ErrorCode map(HttpStatus status, String code) {
        return TossOrderErrorMapper.toErrorCode(status, code);
    }

    @Test
    @DisplayName("400 invalid-request 는 주문 정보 오류로 매핑한다")
    void maps400InvalidRequest() {
        assertThat(map(HttpStatus.BAD_REQUEST, "invalid-request")).isEqualTo(ErrorCode.TOSS_ORDER_INVALID);
    }

    @Test
    @DisplayName("400 confirm-high-value-required 는 금액 확인 필요로 매핑한다")
    void maps400ConfirmHighValue() {
        assertThat(map(HttpStatus.BAD_REQUEST, "confirm-high-value-required"))
                .isEqualTo(ErrorCode.TOSS_ORDER_HIGH_VALUE_CONFIRM_REQUIRED);
    }

    @Test
    @DisplayName("409 request-in-progress 는 처리 중 주문으로 매핑한다")
    void maps409RequestInProgress() {
        // 이 응답에는 절대 자동 재시도를 걸면 안 된다 — 먼저 나간 요청이 성공 중일 수 있다.
        assertThat(map(HttpStatus.CONFLICT, "request-in-progress")).isEqualTo(ErrorCode.TOSS_ORDER_IN_PROGRESS);
    }

    @Test
    @DisplayName("422 insufficient-buying-power 는 잔고 부족으로 매핑한다")
    void maps422InsufficientBuyingPower() {
        assertThat(map(HttpStatus.UNPROCESSABLE_ENTITY, "insufficient-buying-power"))
                .isEqualTo(ErrorCode.TOSS_ORDER_INSUFFICIENT_BUYING_POWER);
    }

    @Test
    @DisplayName("422 order-hours-closed 는 주문 가능 시간 아님으로 매핑한다")
    void maps422OrderHoursClosed() {
        assertThat(map(HttpStatus.UNPROCESSABLE_ENTITY, "order-hours-closed"))
                .isEqualTo(ErrorCode.TOSS_ORDER_HOURS_CLOSED);
    }

    @Test
    @DisplayName("422 idempotency-key-conflict 는 주문 내용 변경으로 매핑한다")
    void maps422IdempotencyConflict() {
        assertThat(map(HttpStatus.UNPROCESSABLE_ENTITY, "idempotency-key-conflict"))
                .isEqualTo(ErrorCode.TOSS_ORDER_IDEMPOTENCY_CONFLICT);
    }

    @Test
    @DisplayName("422 max-order-amount-exceeded 는 주문 한도 초과로 매핑한다")
    void maps422MaxOrderAmountExceeded() {
        assertThat(map(HttpStatus.UNPROCESSABLE_ENTITY, "max-order-amount-exceeded"))
                .isEqualTo(ErrorCode.TOSS_ORDER_MAX_AMOUNT_EXCEEDED);
    }

    @Test
    @DisplayName("422 opposite-pending-order-exists 는 반대 방향 미체결로 매핑한다")
    void maps422OppositePending() {
        assertThat(map(HttpStatus.UNPROCESSABLE_ENTITY, "opposite-pending-order-exists"))
                .isEqualTo(ErrorCode.TOSS_ORDER_OPPOSITE_PENDING);
    }

    @Test
    @DisplayName("422 prerequisite-required 는 사전 동의 필요로 매핑한다")
    void maps422PrerequisiteRequired() {
        assertThat(map(HttpStatus.UNPROCESSABLE_ENTITY, "prerequisite-required"))
                .isEqualTo(ErrorCode.TOSS_ORDER_PREREQUISITE_REQUIRED);
    }

    @Test
    @DisplayName("500 maintenance 는 점검 중으로 매핑한다")
    void maps500Maintenance() {
        assertThat(map(HttpStatus.INTERNAL_SERVER_ERROR, "maintenance")).isEqualTo(ErrorCode.TOSS_MAINTENANCE);
    }

    @Test
    @DisplayName("429 는 코드와 무관하게 요청 한도 초과로 매핑한다")
    void maps429RateLimited() {
        assertThat(map(HttpStatus.TOO_MANY_REQUESTS, "")).isEqualTo(ErrorCode.TOSS_RATE_LIMITED);
    }

    @Test
    @DisplayName("401·403 은 토스 인증 거부로 매핑한다")
    void mapsAuthFailures() {
        assertThat(map(HttpStatus.UNAUTHORIZED, "")).isEqualTo(ErrorCode.TOSS_UNAUTHORIZED);
        assertThat(map(HttpStatus.FORBIDDEN, "")).isEqualTo(ErrorCode.TOSS_UNAUTHORIZED);
    }

    @Test
    @DisplayName("모르는 코드는 상태코드별 기본값으로 떨어진다")
    void fallsBackByStatusForUnknownCodes() {
        // 토스가 코드를 새로 추가해도 500 이 아니라 뜻이 통하는 에러가 나가야 한다.
        assertThat(map(HttpStatus.BAD_REQUEST, "brand-new-code")).isEqualTo(ErrorCode.TOSS_ORDER_INVALID);
        assertThat(map(HttpStatus.CONFLICT, "brand-new-code")).isEqualTo(ErrorCode.TOSS_ORDER_IN_PROGRESS);
        assertThat(map(HttpStatus.UNPROCESSABLE_ENTITY, "brand-new-code")).isEqualTo(ErrorCode.TOSS_ORDER_REJECTED);
        assertThat(map(HttpStatus.INTERNAL_SERVER_ERROR, "brand-new-code")).isEqualTo(ErrorCode.TOSS_API_FAILED);
    }

    @Test
    @DisplayName("상태코드를 해석할 수 없어도 호출 실패로 떨어진다")
    void fallsBackWhenStatusIsNull() {
        assertThat(map(null, null)).isEqualTo(ErrorCode.TOSS_API_FAILED);
    }

    @Test
    @DisplayName("매핑 결과 메시지에 토스 원문이 섞이지 않는다")
    void neverLeaksTossMessages() {
        // 사용자에게 나가는 문구는 우리 ErrorCode 가 단독으로 정한다.
        assertThat(map(HttpStatus.UNPROCESSABLE_ENTITY, "insufficient-buying-power").getMessage())
                .isEqualTo("주문 가능 금액이 부족합니다.");
    }
}
