package com.behcm.domain.admin.knowledgeaccess.service;

import com.behcm.domain.admin.knowledgeaccess.dto.AdminKnowledgeAccessRequest;
import com.behcm.domain.admin.knowledgeaccess.dto.AdminKnowledgeAccessResponse;
import com.behcm.domain.knowledge.entity.KnowledgeAccess;
import com.behcm.domain.knowledge.repository.KnowledgeAccessRepository;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 지식 메모 읽기/쓰기 권한 관리. ADMIN 은 이 테이블과 무관하게 항상 읽기/쓰기가 가능하므로
 * 여기서 다루는 것은 "ADMIN 이 권한을 지정한 유저" 목록이다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminKnowledgeAccessService {

    private final KnowledgeAccessRepository knowledgeAccessRepository;
    private final MemberRepository memberRepository;

    public List<AdminKnowledgeAccessResponse> getAccessList() {
        return knowledgeAccessRepository.findAllWithMember().stream()
                .map(AdminKnowledgeAccessResponse::from)
                .toList();
    }

    /**
     * 멱등하게 동작한다 — 같은 값으로 여러 번 호출해도 결과가 같다.
     * 신규 회원에게 동시에 요청이 와도 uk_knowledge_access_member 위반 없이 한 행으로 수렴하도록 upsert 를 쓴다.
     */
    @Transactional
    public AdminKnowledgeAccessResponse update(Long memberId, AdminKnowledgeAccessRequest request, Member admin) {
        Member target = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        // ADMIN 은 항상 허용되므로 행을 만들지 않는다.
        if (target.getRole() == MemberRole.ADMIN) {
            return AdminKnowledgeAccessResponse.of(target, true, true);
        }

        boolean canRead = request.canRead();
        boolean canWrite = request.canWrite();
        Long grantedBy = admin != null ? admin.getId() : null;

        // 두 권한이 모두 없으면 행을 두지 않는다.
        if (!canRead && !canWrite) {
            knowledgeAccessRepository.deleteByMemberId(memberId);
            log.info("Admin updated knowledge access (memberId={}, canRead={}, canWrite={}, grantedBy={})",
                    memberId, false, false, grantedBy);
            return AdminKnowledgeAccessResponse.of(target, false, false);
        }

        knowledgeAccessRepository.upsert(memberId, canRead, canWrite, grantedBy);
        // upsert 는 영속성 컨텍스트를 비우므로 다시 읽어 응답한다. 다른 사람의 메모를 읽고 고칠 수 있게 되는 순간이라 감사 로그를 남긴다.
        log.info("Admin updated knowledge access (memberId={}, canRead={}, canWrite={}, grantedBy={})",
                memberId, canRead, canWrite, grantedBy);
        KnowledgeAccess saved = knowledgeAccessRepository.findByMemberId(memberId).orElseThrow();
        return AdminKnowledgeAccessResponse.from(saved);
    }
}
