package com.behcm.domain.tossstock.controller;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.tossstock.dto.TossOrderRequest;
import com.behcm.domain.tossstock.dto.TossOrderResponse;
import com.behcm.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 주문 엔드포인트의 인가.
 *
 * <p>이 클래스에서 가장 중요한 테스트는 <b>조회 권한만 있는 회원이 403 을 받는다</b>는 것이다.
 * Spring Security 는 메서드 레벨 {@code @PreAuthorize} 를 발견하면 클래스 레벨을 대체하므로,
 * 누군가 편의로 메서드에 애노테이션을 하나 붙이는 순간 이 컨트롤러의 ADMIN 검사가 통째로 꺼진다.
 * 그 사고를 잡아내라고 있는 테스트다.
 */
class TossOrderControllerTest extends IntegrationTestSupport {

    private Member member(MemberRole role) {
        return Member.builder()
                .email("user@test.com")
                .password("encoded")
                .nickname("user")
                .role(role)
                .build();
    }

    /** 주문 권한이 있는 회원(ADMIN). */
    private Member trader() {
        given(tossAccessChecker.canTrade(any())).willReturn(true);
        return member(MemberRole.ADMIN);
    }

    /** 조회 권한은 있으나 주문 권한은 없는 회원. */
    private Member viewerOnly() {
        given(tossAccessChecker.canAccess(any())).willReturn(true);
        given(tossAccessChecker.canTrade(any())).willReturn(false);
        return member(MemberRole.USER);
    }

    private String orderJson() {
        TossOrderRequest request = new TossOrderRequest();
        request.setOwner("ME");
        request.setSymbol("005930");
        request.setSide("BUY");
        request.setOrderType("LIMIT");
        request.setQuantity("10");
        request.setPrice("70000");
        request.setClientOrderId("key-1");
        return objectMapper.writeValueAsString(request);
    }

    @Test
    @DisplayName("주문은 인증 없이 요청하면 401을 반환한다")
    void placeOrder_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/toss-stock/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("주문은 조회 권한만 있는 회원에게 403을 반환한다")
    void placeOrder_withViewOnlyMember_returnsForbidden() throws Exception {
        // toss_access 는 "가족 자산을 볼 수 있다"는 뜻이지 "주문을 낼 수 있다"는 뜻이 아니다.
        mockMvc.perform(post("/api/toss-stock/orders")
                        .with(user(viewerOnly()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson()))
                .andExpect(status().isForbidden());

        // 인가는 컨트롤러 진입 전에 끝나야 한다 — 서비스까지 흘러가면 안 된다.
        verify(tossOrderService, never()).placeOrder(any());
    }

    @Test
    @DisplayName("주문은 ADMIN이면 주문번호를 반환한다")
    void placeOrder_withAdmin_returnsOrderId() throws Exception {
        given(tossOrderService.placeOrder(any())).willReturn(new TossOrderResponse("order-1", "key-1"));

        mockMvc.perform(post("/api/toss-stock/orders")
                        .with(user(trader()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId", is("order-1")))
                .andExpect(jsonPath("$.data.clientOrderId", is("key-1")));
    }

    @Test
    @DisplayName("주문은 수량이 0이면 400을 반환한다")
    void placeOrder_withZeroQuantity_returnsBadRequest() throws Exception {
        TossOrderRequest request = new TossOrderRequest();
        request.setOwner("ME");
        request.setSymbol("005930");
        request.setSide("BUY");
        request.setOrderType("LIMIT");
        request.setQuantity("-1");
        request.setPrice("70000");

        mockMvc.perform(post("/api/toss-stock/orders")
                        .with(user(trader()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(tossOrderService, never()).placeOrder(any());
    }

    @Test
    @DisplayName("주문은 멱등키 형식이 잘못되면 400을 반환한다")
    void placeOrder_withMalformedClientOrderId_returnsBadRequest() throws Exception {
        TossOrderRequest request = new TossOrderRequest();
        request.setOwner("ME");
        request.setSymbol("005930");
        request.setSide("BUY");
        request.setOrderType("LIMIT");
        request.setQuantity("10");
        request.setPrice("70000");
        request.setClientOrderId("키/에 슬래시");

        mockMvc.perform(post("/api/toss-stock/orders")
                        .with(user(trader()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("미체결 목록은 조회 권한만 있는 회원에게 403을 반환한다")
    void getOpenOrders_withViewOnlyMember_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/toss-stock/orders/open")
                        .param("owner", "ME")
                        .with(user(viewerOnly())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("미체결 목록은 ADMIN이면 목록을 반환한다")
    void getOpenOrders_withAdmin_returnsList() throws Exception {
        given(tossOrderService.getOpenOrders(any())).willReturn(List.of());

        mockMvc.perform(get("/api/toss-stock/orders/open")
                        .param("owner", "ME")
                        .with(user(trader())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("미체결 목록은 알 수 없는 owner 를 400으로 거부한다")
    void getOpenOrders_withUnknownOwner_returnsBadRequest() throws Exception {
        // Spring 기본 enum 컨버터에 맡기면 500 이 나간다. 직접 변환해 400 으로 떨어뜨린다.
        mockMvc.perform(get("/api/toss-stock/orders/open")
                        .param("owner", "STRANGER")
                        .with(user(trader())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("주문 취소는 조회 권한만 있는 회원에게 403을 반환한다")
    void cancelOrder_withViewOnlyMember_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/toss-stock/orders/order-1/cancel")
                        .with(user(viewerOnly()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"ME\"}"))
                .andExpect(status().isForbidden());

        verify(tossOrderService, never()).cancelOrder(any(), any());
    }

    @Test
    @DisplayName("주문 취소는 ADMIN이면 취소한 주문번호를 반환한다")
    void cancelOrder_withAdmin_returnsOrderId() throws Exception {
        given(tossOrderService.cancelOrder(any(), any())).willReturn("order-1");

        mockMvc.perform(post("/api/toss-stock/orders/order-1/cancel")
                        .with(user(trader()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"ME\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId", is("order-1")));
    }
}
