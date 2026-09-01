package com.behcm.domain.tossstock.dto;

/**
 * 연동된 계좌 소유자. 프론트의 계좌 전환 세그먼트를 채우는 데 쓴다.
 *
 * @param owner       요청 파라미터로 되돌려 보낼 식별자 (ME, MOM, DAD)
 * @param displayName 화면에 표시할 이름 (나, 엄마, 아빠)
 */
public record TossOwnerResponse(String owner, String displayName) { }
