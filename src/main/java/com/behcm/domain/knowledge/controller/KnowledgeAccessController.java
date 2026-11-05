package com.behcm.domain.knowledge.controller;

import com.behcm.domain.knowledge.dto.KnowledgeAccessResponse;
import com.behcm.domain.knowledge.service.KnowledgeAccessChecker;
import com.behcm.domain.member.entity.Member;
import com.behcm.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "나에게 지식 메모 접근 권한이 있는가"를 확인하는 엔드포인트.
 *
 * <p>지식 메모 본 컨트롤러와 경로 prefix 는 같지만 클래스를 분리했다. 그쪽 클래스 레벨
 * {@code @PreAuthorize} 아래에 두면 권한 없는 회원이 403 을 받아, 권한 없음을 확인할 수단 자체가 사라진다.
 * 여기는 인가 규칙 없이 {@code anyRequest().authenticated()} 만 적용된다 — 로그인만 하면 200 이고,
 * 권한 여부는 응답 본문으로 알려준다.
 */
@RestController
@RequestMapping("/api/knowledge-notes")
@RequiredArgsConstructor
public class KnowledgeAccessController {

    private final KnowledgeAccessChecker knowledgeAccessChecker;

    @GetMapping("/access")
    public ResponseEntity<ApiResponse<KnowledgeAccessResponse>> getAccess(
            @AuthenticationPrincipal Member member
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                KnowledgeAccessResponse.from(knowledgeAccessChecker.permissionsOf(member))));
    }
}
