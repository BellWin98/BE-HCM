package com.behcm.domain.admin.knowledgeaccess.controller;

import com.behcm.domain.admin.knowledgeaccess.dto.AdminKnowledgeAccessRequest;
import com.behcm.domain.admin.knowledgeaccess.dto.AdminKnowledgeAccessResponse;
import com.behcm.domain.admin.knowledgeaccess.service.AdminKnowledgeAccessService;
import com.behcm.domain.member.entity.Member;
import com.behcm.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 지식 메모 읽기/쓰기 권한 관리(ADMIN 전용). 회원 관리 화면의 "지식 메모" 권한 토글이 이 API 를 호출한다.
 */
@RestController
@RequestMapping("/api/admin/knowledge-access")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminKnowledgeAccessController {

    private final AdminKnowledgeAccessService adminKnowledgeAccessService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminKnowledgeAccessResponse>>> getAccessList() {
        return ResponseEntity.ok(ApiResponse.success(adminKnowledgeAccessService.getAccessList()));
    }

    @PutMapping("/{memberId}")
    public ResponseEntity<ApiResponse<AdminKnowledgeAccessResponse>> update(
            @PathVariable Long memberId,
            @Valid @RequestBody AdminKnowledgeAccessRequest request,
            @AuthenticationPrincipal Member admin
    ) {
        AdminKnowledgeAccessResponse response = adminKnowledgeAccessService.update(memberId, request, admin);
        return ResponseEntity.ok(ApiResponse.success("지식 메모 권한을 변경했습니다.", response));
    }
}
