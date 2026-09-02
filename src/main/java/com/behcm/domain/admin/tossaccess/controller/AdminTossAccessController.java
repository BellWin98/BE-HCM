package com.behcm.domain.admin.tossaccess.controller;

import com.behcm.domain.admin.tossaccess.dto.AdminTossAccessResponse;
import com.behcm.domain.admin.tossaccess.service.AdminTossAccessService;
import com.behcm.domain.member.entity.Member;
import com.behcm.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 토스증권 접근 권한 관리(ADMIN 전용). 회원 관리 화면의 "토스 접근" 토글이 이 API 를 호출한다.
 */
@RestController
@RequestMapping("/api/admin/toss-access")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminTossAccessController {

    private final AdminTossAccessService adminTossAccessService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminTossAccessResponse>>> getGrantedMembers() {
        return ResponseEntity.ok(ApiResponse.success(adminTossAccessService.getGrantedMembers()));
    }

    @PostMapping("/{memberId}")
    public ResponseEntity<ApiResponse<AdminTossAccessResponse>> grant(
            @PathVariable Long memberId,
            @AuthenticationPrincipal Member admin
    ) {
        AdminTossAccessResponse response = adminTossAccessService.grant(memberId, admin);
        return ResponseEntity.ok(ApiResponse.success("토스 접근 권한을 부여했습니다.", response));
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<ApiResponse<Void>> revoke(@PathVariable Long memberId) {
        adminTossAccessService.revoke(memberId);
        return ResponseEntity.ok(ApiResponse.success("토스 접근 권한을 회수했습니다.", null));
    }
}
