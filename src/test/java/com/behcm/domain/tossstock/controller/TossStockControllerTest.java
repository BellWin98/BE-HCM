package com.behcm.domain.tossstock.controller;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.tossstock.dto.TossOwnerResponse;
import com.behcm.domain.tossstock.dto.TossPortfolioResponse;
import com.behcm.domain.tossstock.dto.TossRealizedProfitRequest;
import com.behcm.domain.tossstock.dto.TossRealizedProfitResponse;
import com.behcm.domain.tossstock.service.TossAccessChecker;
import com.behcm.domain.tossstock.service.TossStockService;
import com.behcm.global.config.toss.TossAccountOwner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인가는 role 이 아니라 {@link TossAccessChecker} 가 판정하므로, 여기서는 checker 를 목으로 두고
 * 허용/차단이 컨트롤러에 반영되는지만 본다. ADMIN 우대나 toss_access 조회 같은 판정 규칙 자체는
 * TossAccessCheckerTest 가 담당한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TossStockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TossStockService tossStockService;

    @MockitoBean
    private TossAccessChecker tossAccessChecker;

    private Member member(MemberRole role) {
        return Member.builder()
                .email("user@test.com")
                .password("encoded")
                .nickname("user")
                .role(role)
                .build();
    }

    /** 토스 접근이 허용된 회원. 역할과 무관하게 toss_access 에 등록되어 있으면 통과한다. */
    private Member grantedMember() {
        given(tossAccessChecker.canAccess(any())).willReturn(true);
        return member(MemberRole.USER);
    }

    private Member deniedMember() {
        given(tossAccessChecker.canAccess(any())).willReturn(false);
        return member(MemberRole.USER);
    }

    private TossRealizedProfitRequest realizedProfitRequest() {
        TossRealizedProfitRequest request = new TossRealizedProfitRequest();
        request.setOwner("ME");
        request.setStartDate("2026-01-01");
        request.setEndDate("2026-01-31");
        return request;
    }

    @Test
    @DisplayName("getPortfolio는 인증 없이 요청하면 401을 반환한다")
    void getPortfolio_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/toss-stock/portfolio").param("owner", "ME"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("getPortfolio는 토스 접근 권한이 없으면 403을 반환한다")
    void getPortfolio_withoutTossAccess_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/toss-stock/portfolio")
                        .param("owner", "ME")
                        .with(user(deniedMember())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getPortfolio는 FAMILY 역할만으로는 접근할 수 없다")
    void getPortfolio_withFamilyRoleButNoGrant_returnsForbidden() throws Exception {
        given(tossAccessChecker.canAccess(any())).willReturn(false);

        mockMvc.perform(get("/api/toss-stock/portfolio")
                        .param("owner", "ME")
                        .with(user(member(MemberRole.FAMILY))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getPortfolio는 토스 접근 권한이 있으면 조회 결과를 반환한다")
    void getPortfolio_withTossAccess_returnsPortfolio() throws Exception {
        given(tossStockService.getPortfolio(TossAccountOwner.ME)).willReturn(
                TossPortfolioResponse.builder()
                        .owner("ME")
                        .ownerName("나")
                        .totalMarketValueKrw(new BigDecimal("7200000"))
                        .totalProfitLossRate(new BigDecimal("15.16"))
                        .holdings(List.of())
                        .build()
        );

        mockMvc.perform(get("/api/toss-stock/portfolio")
                        .param("owner", "ME")
                        .with(user(grantedMember())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerName", is("나")))
                .andExpect(jsonPath("$.data.totalProfitLossRate", is(15.16)));
    }

    @Test
    @DisplayName("getPortfolio는 알 수 없는 owner를 400으로 거부한다")
    void getPortfolio_withUnknownOwner_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/toss-stock/portfolio")
                        .param("owner", "UNCLE")
                        .with(user(grantedMember())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("getOwners는 토스 접근 권한이 있으면 연동된 계좌 목록을 반환한다")
    void getOwners_withTossAccess_returnsConfiguredOwners() throws Exception {
        given(tossStockService.getOwners()).willReturn(List.of(
                new TossOwnerResponse("ME", "나"),
                new TossOwnerResponse("MOM", "엄마")
        ));

        mockMvc.perform(get("/api/toss-stock/owners").with(user(grantedMember())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].owner", is("ME")))
                .andExpect(jsonPath("$.data[1].displayName", is("엄마")));
    }

    @Test
    @DisplayName("getOwners는 토스 접근 권한이 없으면 403을 반환한다")
    void getOwners_withoutTossAccess_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/toss-stock/owners").with(user(deniedMember())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getRealizedProfit은 토스 접근 권한이 없으면 403을 반환한다")
    void getRealizedProfit_withoutTossAccess_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/toss-stock/realized-profit")
                        .with(user(deniedMember()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(realizedProfitRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getRealizedProfit은 날짜 형식이 잘못되면 400을 반환한다")
    void getRealizedProfit_withMalformedDate_returnsBadRequest() throws Exception {
        TossRealizedProfitRequest request = realizedProfitRequest();
        request.setStartDate("2026/01/01");

        mockMvc.perform(post("/api/toss-stock/realized-profit")
                        .with(user(grantedMember()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("getRealizedProfit은 토스 접근 권한이 있으면 계산 결과를 반환한다")
    void getRealizedProfit_withTossAccess_returnsRealizedProfit() throws Exception {
        given(tossStockService.getRealizedProfit(any())).willReturn(
                TossRealizedProfitResponse.builder()
                        .owner("ME")
                        .ownerName("나")
                        .period("2026-01-01 ~ 2026-01-31")
                        .totals(List.of())
                        .tradeCount(0)
                        .trades(List.of())
                        .estimated(false)
                        .build()
        );

        mockMvc.perform(post("/api/toss-stock/realized-profit")
                        .with(user(grantedMember()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(realizedProfitRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period", is("2026-01-01 ~ 2026-01-31")))
                .andExpect(jsonPath("$.data.estimated", is(false)));
    }
}
