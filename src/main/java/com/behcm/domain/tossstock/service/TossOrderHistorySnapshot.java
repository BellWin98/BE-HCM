package com.behcm.domain.tossstock.service;

import com.behcm.domain.tossstock.service.TossRealizedProfitCalculator.Fill;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 주문내역 스냅샷. 체결·종목명·동기화 시각을 <b>한 객체</b>로 묶는 것이 중요하다 — 따로 저장하면
 * 한쪽만 남아 영구히 어긋난 상태가 생길 수 있다. 하나면 교체·만료가 구조적으로 원자적이다.
 *
 * @param byOrderId 주문 식별자 → 체결. 병합이 덧붙이기가 아니라 교체여야 해서 주문 단위로 든다({@link OrderFill}).
 * @param names     심볼 → 종목명
 * @param syncedAt  이 스냅샷을 만든 동기화의 <b>시작</b> 시각. 다음 재조회 창의 기준이다.
 */
record TossOrderHistorySnapshot(Map<String, OrderFill> byOrderId, Map<String, String> names, LocalDateTime syncedAt) {

    /**
     * 체결 1건. {@code orderId} 를 함께 들고 있는 이유는 <b>병합이 덧붙이기가 아니라
     * 교체</b>여야 하기 때문이다 — {@code PARTIAL_FILLED} 는 미체결·종료 두 그룹에 모두 속해서 같은
     * 주문의 체결 수량이 나중에 늘어난다. 덧붙이면 같은 체결이 두 번 계산되어 손익이 두 배가 된다.
     *
     * <p>{@code orderedAt} 은 재조회 창의 경계 판정에 쓴다(체결 시각이 아니다 — API 의 필터 축이
     * 접수 시각이다).
     */
    record OrderFill(String orderId, LocalDateTime orderedAt, Fill fill) { }
}
