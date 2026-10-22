package com.behcm.global.config.toss;

/**
 * 쓰기 요청({@link TossInvestClient#post})을 언제 다시 보내도 되는가.
 *
 * <p>조회와 달리 쓰기는 "한 번 더 보낸 요청 = 한 건 더"가 될 수 있어, 재전송 조건을 요청마다 명시한다.
 * 401(토큰 만료)은 요청이 원장에 닿기 전에 거부된 것이라 모든 정책에서 1회 재발급 후 재시도한다.
 *
 * <p>package-private 이다 — 쓰기 경로가 이 패키지 밖으로 새지 않는다는 {@link TossInvestClient} 의 경계를 따른다.
 */
enum WriteRetryPolicy {

    /** 401 외에는 다시 보내지 않는다. */
    NONE,

    /**
     * 429 만 다시 보낸다. 한도 응답은 요청을 처리하지 않았다는 뜻이라 안전하다.
     * 타임아웃은 다시 보내지 않는다 — 취소처럼 멱등키가 없는 요청은 첫 요청이 이미 닿았다면
     * 재전송이 "이미 취소됨" 같은 에러로 돌아와, 성공한 요청을 실패로 보여 준다.
     */
    RATE_LIMIT,

    /**
     * 본문에 멱등키({@code clientOrderId})가 실린 요청. 429 에 더해 <b>타임아웃</b>과
     * {@code 409 request-in-progress} 도 다시 보낸다 — 토스는 같은 키 + 같은 본문이면 새로 만들지 않고
     * 기존 결과를 돌려주므로(10분간), 재전송이 곧 "접수됐는지 다시 묻기"다.
     */
    IDEMPOTENCY_KEY;

    boolean retriesRateLimit() {
        return this != NONE;
    }

    boolean resendsAfterTimeout() {
        return this == IDEMPOTENCY_KEY;
    }

    boolean retriesInProgress() {
        return this == IDEMPOTENCY_KEY;
    }
}
