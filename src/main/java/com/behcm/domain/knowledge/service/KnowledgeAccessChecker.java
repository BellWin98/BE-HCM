package com.behcm.domain.knowledge.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import org.springframework.stereotype.Component;

/**
 * 지식 메모 접근 판정. 지식 메모 컨트롤러의 {@code @PreAuthorize} SpEL 에서 참조한다.
 *
 * <p>인가는 이 한 곳에서만 판정한다 — {@code SecurityConfig} 의 URL 규칙에 같은 조건을 중복하면
 * 판정 로직이 두 군데로 갈라진다. 현재는 ADMIN 만 허용하며, 나중에 지정한 유저에게도 허용해야 하면
 * 이 메서드만 바꾸면 된다. role 만 보므로 DB 조회가 없다.
 */
@Component("knowledgeAccessChecker")
public class KnowledgeAccessChecker {

    /**
     * principal 을 {@code Member} 가 아니라 {@code Object} 로 받는 이유: 익명 인증의 principal 은
     * {@code String("anonymousUser")} 라서, 타입을 좁히면 SpEL 이 변환에 실패해 403 대신 500 이 나간다.
     */
    public boolean canAccess(Object principal) {
        return principal instanceof Member member && member.getRole() == MemberRole.ADMIN;
    }
}
