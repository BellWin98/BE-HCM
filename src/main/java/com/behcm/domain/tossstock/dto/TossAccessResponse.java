package com.behcm.domain.tossstock.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 요청한 회원 본인의 토스 접근 권한 여부. 프론트 라우트 가드가 화면 진입 전에 확인한다.
 */
@Getter
@AllArgsConstructor
public class TossAccessResponse {

    private boolean hasAccess;
}
