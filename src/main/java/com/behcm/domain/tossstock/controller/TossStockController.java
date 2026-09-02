package com.behcm.domain.tossstock.controller;

import com.behcm.domain.tossstock.dto.TossOwnerResponse;
import com.behcm.domain.tossstock.dto.TossPortfolioResponse;
import com.behcm.domain.tossstock.dto.TossRealizedProfitRequest;
import com.behcm.domain.tossstock.dto.TossRealizedProfitResponse;
import com.behcm.domain.tossstock.service.TossStockService;
import com.behcm.global.common.ApiResponse;
import com.behcm.global.config.toss.TossAccountOwner;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 토스증권 조회 API. 한국투자증권({@code /api/stock})과 경로부터 분리한다.
 *
 * <p>{@code owner} 를 enum 이 아니라 문자열로 받는 이유: Spring 의 기본 enum 컨버터는 변환 실패 시
 * MethodArgumentTypeMismatchException 을 던지는데 GlobalExceptionHandler 가 이를 다루지 않아 500 이 나간다.
 * 직접 변환해 400 으로 떨어뜨린다.
 *
 * <p>접근 권한은 role 이 아니라 {@code TossAccessChecker} 가 판정한다 — ADMIN 이거나 {@code toss_access} 에
 * 등록된 회원만 허용한다. {@code @PreAuthorize} 를 클래스 레벨에 두는 것은 의도적이다: 엔드포인트가
 * 추가돼도 기본이 차단이어야 한다.
 */
@RestController
@RequestMapping("/api/toss-stock")
@RequiredArgsConstructor
@PreAuthorize("@tossAccessChecker.canAccess(authentication.principal)")
public class TossStockController {

    private final TossStockService tossStockService;

    @GetMapping("/owners")
    public ResponseEntity<ApiResponse<List<TossOwnerResponse>>> getOwners() {
        return ResponseEntity.ok(ApiResponse.success(tossStockService.getOwners()));
    }

    @GetMapping("/portfolio")
    public ResponseEntity<ApiResponse<TossPortfolioResponse>> getPortfolio(
            @RequestParam("owner") String owner
    ) {
        TossPortfolioResponse portfolio = tossStockService.getPortfolio(TossAccountOwner.from(owner));
        return ResponseEntity.ok(ApiResponse.success(portfolio));
    }

    @PostMapping("/realized-profit")
    public ResponseEntity<ApiResponse<TossRealizedProfitResponse>> getRealizedProfit(
            @Valid @RequestBody TossRealizedProfitRequest request
    ) {
        TossRealizedProfitResponse realizedProfit = tossStockService.getRealizedProfit(request);
        return ResponseEntity.ok(ApiResponse.success(realizedProfit));
    }
}
