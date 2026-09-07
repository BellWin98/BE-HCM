package com.behcm.domain.tossstock.controller;

import com.behcm.domain.tossstock.dto.TossOpenOrderResponse;
import com.behcm.domain.tossstock.dto.TossOrderCancelRequest;
import com.behcm.domain.tossstock.dto.TossOrderCancelResponse;
import com.behcm.domain.tossstock.dto.TossOrderRequest;
import com.behcm.domain.tossstock.dto.TossOrderResponse;
import com.behcm.domain.tossstock.service.TossOrderService;
import com.behcm.global.common.ApiResponse;
import com.behcm.global.config.toss.TossAccountOwner;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 토스증권 주문 API. 실제로 돈이 움직이는 엔드포인트만 모아 둔다.
 *
 * <p>{@code TossStockController}(조회)와 클래스를 나눈 것은 <b>인가가 다르기 때문</b>이다.
 * 조회는 {@code toss_access} 를 받은 가족 전원, 주문은 ADMIN 만이다.
 * 같은 경로 prefix 아래 클래스를 나누는 방식은 {@code TossAccessController} 가 이미 쓰고 있다.
 *
 * <p><b>이 클래스에는 메서드 레벨 {@code @PreAuthorize} 를 붙이지 않는다.</b>
 * Spring Security 는 메서드 레벨 애노테이션을 찾으면 클래스 레벨을 <b>대체</b>한다(AND 가 아니다).
 * 메서드에 하나라도 붙는 순간 여기 클래스 레벨 검사가 통째로 꺼져 조용히 열린다.
 * 엔드포인트별로 권한을 달리해야 한다면 클래스를 하나 더 만드는 편이 안전하다.
 */
@RestController
@RequestMapping("/api/toss-stock/orders")
@RequiredArgsConstructor
@PreAuthorize("@tossAccessChecker.canTrade(authentication.principal)")
public class TossOrderController {

    private final TossOrderService tossOrderService;

    /**
     * 체결 대기 중인 주문. 주문을 낼 수 있게 된 이상 낸 것이 살아 있는지 볼 수단이 반드시 있어야 한다.
     */
    @GetMapping("/open")
    public ResponseEntity<ApiResponse<List<TossOpenOrderResponse>>> getOpenOrders(
            @RequestParam("owner") String owner
    ) {
        List<TossOpenOrderResponse> orders = tossOrderService.getOpenOrders(TossAccountOwner.from(owner));
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TossOrderResponse>> placeOrder(
            @Valid @RequestBody TossOrderRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("주문이 접수되었습니다.", tossOrderService.placeOrder(request)));
    }

    /**
     * 소유자를 쿼리가 아니라 본문으로 받는다 — 취소는 상태를 바꾸는 요청이라
     * 링크·로그·리퍼러에 계좌 정보가 남지 않는 편이 낫다.
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<TossOrderCancelResponse>> cancelOrder(
            @PathVariable("orderId") String orderId,
            @Valid @RequestBody TossOrderCancelRequest request
    ) {
        String canceled = tossOrderService.cancelOrder(TossAccountOwner.from(request.getOwner()), orderId);
        return ResponseEntity.ok(ApiResponse.success("주문이 취소되었습니다.", new TossOrderCancelResponse(canceled)));
    }
}
