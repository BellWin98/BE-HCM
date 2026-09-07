package com.behcm.global.config.toss;

import com.behcm.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * 토스 주문 에러({@code error.code}) → 우리 {@link ErrorCode}.
 *
 * <p>조회 경로는 상태코드만으로 충분했다(인증·한도·장애 셋뿐이다). 주문은 다르다 —
 * 거부 사유가 곧 사용자가 다음에 할 행동을 정한다. 잔고를 채울지, 장이 열리길 기다릴지,
 * 가격을 고칠지, 토스 앱에 가서 동의를 할지가 전부 다르므로 {@code code} 단위로 갈라야 한다.
 *
 * <p><b>토스의 원문 메시지는 사용자 응답에 싣지 않는다.</b> 문구가 예고 없이 바뀌면 화면 문구가 함께
 * 흔들리고, 내부 용어가 그대로 새기도 한다. 원문은 {@code requestId} 와 함께 로그에만 남긴다.
 *
 * <p>모르는 코드는 500 으로 흘리지 않고 상태코드별 기본값으로 떨어뜨린다 —
 * 토스가 코드를 새로 추가해도 뜻이 통하는 에러가 나가야 한다.
 */
final class TossOrderErrorMapper {

    private static final Map<String, ErrorCode> BY_CODE = Map.ofEntries(
            // 400 — 요청 자체가 규격에 맞지 않는다.
            Map.entry("invalid-request", ErrorCode.TOSS_ORDER_INVALID),
            Map.entry("account-header-required", ErrorCode.TOSS_ORDER_INVALID),
            Map.entry("confirm-high-value-required", ErrorCode.TOSS_ORDER_HIGH_VALUE_CONFIRM_REQUIRED),

            // 409 — 같은 멱등키로 먼저 나간 요청이 아직 처리 중이다.
            Map.entry("request-in-progress", ErrorCode.TOSS_ORDER_IN_PROGRESS),

            // 422 — 규격은 맞지만 지금 이 계좌·종목·시각에 허용되지 않는다.
            Map.entry("insufficient-buying-power", ErrorCode.TOSS_ORDER_INSUFFICIENT_BUYING_POWER),
            Map.entry("order-hours-closed", ErrorCode.TOSS_ORDER_HOURS_CLOSED),
            Map.entry("amount-order-outside-regular-hours", ErrorCode.TOSS_ORDER_HOURS_CLOSED),
            Map.entry("fractional-quantity-outside-regular-hours", ErrorCode.TOSS_ORDER_HOURS_CLOSED),
            Map.entry("stock-restricted", ErrorCode.TOSS_ORDER_STOCK_RESTRICTED),
            Map.entry("market-not-supported-for-stock", ErrorCode.TOSS_ORDER_STOCK_RESTRICTED),
            Map.entry("price-out-of-range", ErrorCode.TOSS_ORDER_PRICE_OUT_OF_RANGE),
            Map.entry("opposite-pending-order-exists", ErrorCode.TOSS_ORDER_OPPOSITE_PENDING),
            Map.entry("order-type-not-allowed", ErrorCode.TOSS_ORDER_TYPE_NOT_ALLOWED),
            Map.entry("prerequisite-required", ErrorCode.TOSS_ORDER_PREREQUISITE_REQUIRED),
            Map.entry("investor-exchange-not-integrated", ErrorCode.TOSS_ORDER_PREREQUISITE_REQUIRED),
            Map.entry("account-restricted", ErrorCode.TOSS_ORDER_ACCOUNT_RESTRICTED),
            Map.entry("max-order-amount-exceeded", ErrorCode.TOSS_ORDER_MAX_AMOUNT_EXCEEDED),
            Map.entry("idempotency-key-conflict", ErrorCode.TOSS_ORDER_IDEMPOTENCY_CONFLICT),

            // 500 — 토스 쪽 사정.
            Map.entry("maintenance", ErrorCode.TOSS_MAINTENANCE)
    );

    private TossOrderErrorMapper() {
    }

    static ErrorCode toErrorCode(HttpStatus status, String code) {
        // 인증·한도는 코드와 무관하게 상태코드로 판정한다. 조회 경로와 같은 규칙이라 여기서 먼저 걸러낸다.
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            return ErrorCode.TOSS_RATE_LIMITED;
        }
        if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
            return ErrorCode.TOSS_UNAUTHORIZED;
        }

        if (code != null && !code.isBlank()) {
            ErrorCode mapped = BY_CODE.get(code.trim());
            if (mapped != null) {
                return mapped;
            }
        }
        return fallbackFor(status);
    }

    private static ErrorCode fallbackFor(HttpStatus status) {
        if (status == null) {
            return ErrorCode.TOSS_API_FAILED;
        }
        return switch (status) {
            case BAD_REQUEST -> ErrorCode.TOSS_ORDER_INVALID;
            case CONFLICT -> ErrorCode.TOSS_ORDER_IN_PROGRESS;
            case UNPROCESSABLE_ENTITY -> ErrorCode.TOSS_ORDER_REJECTED;
            default -> ErrorCode.TOSS_API_FAILED;
        };
    }
}
