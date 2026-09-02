package com.behcm.domain.tossstock.controller;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.tossstock.dto.TossAccessResponse;
import com.behcm.domain.tossstock.service.TossAccessChecker;
import com.behcm.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "나에게 토스 접근 권한이 있는가"를 확인하는 엔드포인트.
 *
 * <p>{@code TossStockController} 와 경로 prefix 는 같지만 클래스를 분리했다. 그쪽 클래스 레벨
 * {@code @PreAuthorize} 아래에 두면 권한 없는 회원이 403 을 받아, 권한 없음을 확인할 수단 자체가 사라진다.
 * 여기는 인가 규칙 없이 {@code anyRequest().authenticated()} 만 적용된다 — 로그인만 하면 200 이고,
 * 권한 여부는 응답 본문으로 알려준다.
 */
@RestController
@RequestMapping("/api/toss-stock")
@RequiredArgsConstructor
public class TossAccessController {

    private final TossAccessChecker tossAccessChecker;

    @GetMapping("/access")
    public ResponseEntity<ApiResponse<TossAccessResponse>> getMyAccess(
            @AuthenticationPrincipal Member member
    ) {
        boolean hasAccess = tossAccessChecker.canAccess(member);
        return ResponseEntity.ok(ApiResponse.success(new TossAccessResponse(hasAccess)));
    }
}
