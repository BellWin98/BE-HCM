package com.behcm.domain.knowledge.service;

import com.behcm.domain.knowledge.repository.KnowledgeAccessRepository;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지식 메모 접근 판정. 지식 메모 컨트롤러의 {@code @PreAuthorize} SpEL 에서 참조한다.
 *
 * <p>인가는 이 한 곳에서만 판정한다 — {@code SecurityConfig} 의 URL 규칙에 같은 조건을 중복하면
 * 판정 로직이 두 군데로 갈라진다. ADMIN 은 항상 읽기·쓰기를 가지고, 그 외 회원은 관리자가
 * 부여한 {@code knowledge_access} 행의 권한만 가진다(행이 없으면 권한 없음). ADMIN 은 role 만 보므로
 * DB 를 조회하지 않는다.
 *
 * <p>여기서는 "이 동작을 할 수 있는 회원인가"만 본다. "이 메모에 할 수 있는가"(소유권·공개 범위)는
 * 서비스가 메모를 읽은 뒤 {@link KnowledgePermissions} 로 판단한다.
 */
@Component("knowledgeAccessChecker")
@RequiredArgsConstructor
public class KnowledgeAccessChecker {

    private final KnowledgeAccessRepository knowledgeAccessRepository;

    /**
     * principal 을 {@code Member} 가 아니라 {@code Object} 로 받는 이유: 익명 인증의 principal 은
     * {@code String("anonymousUser")} 라서, 타입을 좁히면 SpEL 이 변환에 실패해 403 대신 500 이 나간다.
     */
    @Transactional(readOnly = true)
    public KnowledgePermissions permissionsOf(Object principal) {
        if (!(principal instanceof Member member) || member.getId() == null) {
            return KnowledgePermissions.NONE;
        }
        if (member.getRole() == MemberRole.ADMIN) {
            return KnowledgePermissions.ADMIN;
        }
        return knowledgeAccessRepository.findByMemberId(member.getId())
                .map(access -> new KnowledgePermissions(false, access.isCanRead(), access.isCanWrite()))
                .orElse(KnowledgePermissions.NONE);
    }

    public boolean canAccess(Object principal) {
        return permissionsOf(principal).hasAccess();
    }

    public boolean canWrite(Object principal) {
        return permissionsOf(principal).canWrite();
    }
}
