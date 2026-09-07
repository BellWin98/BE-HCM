package com.behcm.domain.tossstock.controller;

import com.behcm.domain.tossstock.dto.TossOrderableResponse;
import com.behcm.domain.tossstock.dto.TossOwnerResponse;
import com.behcm.domain.tossstock.dto.TossPortfolioResponse;
import com.behcm.domain.tossstock.dto.TossRealizedProfitRequest;
import com.behcm.domain.tossstock.dto.TossRealizedProfitResponse;
import com.behcm.domain.tossstock.dto.TossStockSearchResponse;
import com.behcm.domain.tossstock.service.TossOrderService;
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
    private final TossOrderService tossOrderService;

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

    /**
     * 종목 검색. 주문이 아니라 조회이므로 주문 권한 없이도 쓸 수 있다.
     *
     * <p>토스에는 검색 API 가 없어 우리가 들고 있는 유니버스에서 찾는다 — 외부 호출이 없으므로
     * 타이핑마다 불려도 비용이 거의 없고, 계좌와 무관한 시장 데이터라 {@code owner} 도 받지 않는다.
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<TossStockSearchResponse>>> searchStocks(
            @RequestParam("query") String query,
            @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(tossOrderService.searchStocks(query, limit)));
    }

    /**
     * 주문 화면을 채울 값 한 벌(현재가·상하한가·매수가능금액·매도가능수량).
     * 토스에서는 네 개의 다른 엔드포인트라 서버가 모아서 한 번에 준다.
     */
    @GetMapping("/orderable")
    public ResponseEntity<ApiResponse<TossOrderableResponse>> getOrderable(
            @RequestParam("owner") String owner,
            @RequestParam("symbol") String symbol,
            @RequestParam(value = "side", defaultValue = "BUY") String side
    ) {
        TossOrderableResponse orderable =
                tossOrderService.getOrderable(TossAccountOwner.from(owner), symbol, side);
        return ResponseEntity.ok(ApiResponse.success(orderable));
    }

    @PostMapping("/realized-profit")
    public ResponseEntity<ApiResponse<TossRealizedProfitResponse>> getRealizedProfit(
            @Valid @RequestBody TossRealizedProfitRequest request
    ) {
        TossRealizedProfitResponse realizedProfit = tossStockService.getRealizedProfit(request);
        return ResponseEntity.ok(ApiResponse.success(realizedProfit));
    }
}
