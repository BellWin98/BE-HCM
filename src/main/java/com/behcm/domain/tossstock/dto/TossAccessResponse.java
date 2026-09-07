package com.behcm.domain.tossstock.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 요청한 회원 본인의 토스 권한. 프론트 라우트 가드가 화면 진입 전에 확인한다.
 *
 * <p>조회({@code hasAccess})와 주문({@code canTrade})을 따로 내려준다 — 둘의 기준이 다르기 때문이다.
 * 조회는 {@code toss_access} 에 등록된 회원까지, 주문은 ADMIN 만이다.
 * {@code canTrade} 는 <b>화면 표시 제어용</b>이다. 실제 차단은 언제나 서버가 한다
 * (프론트가 이 값을 조작해도 주문 엔드포인트가 403 을 낸다).
 */
@Getter
@AllArgsConstructor
public class TossAccessResponse {

    private boolean hasAccess;
    private boolean canTrade;
}
