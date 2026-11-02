package com.behcm.domain.knowledge.dto;

/**
 * 요청한 회원 본인의 지식 메모 접근 권한. 프론트 라우트 가드가 화면 진입 전에 확인한다.
 * 표시 제어용이며, 실제 차단은 언제나 서버({@code KnowledgeAccessChecker})가 한다.
 */
public record KnowledgeAccessResponse(boolean hasAccess) {
}
