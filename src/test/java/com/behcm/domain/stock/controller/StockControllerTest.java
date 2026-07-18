package com.behcm.domain.stock.controller;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.stock.dto.StockPortfolioResponse;
import com.behcm.domain.stock.dto.TradingProfitLossRequest;
import com.behcm.domain.stock.dto.TradingProfitLossResponse;
import com.behcm.domain.stock.service.StockService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StockService stockService;

    private Member member() {
        return Member.builder()
                .email("user@test.com")
                .password("encoded")
                .nickname("user")
                .role(MemberRole.USER)
                .build();
    }

    @Test
    @DisplayName("getStockPortfolio는 인증 없이 요청하면 401을 반환한다")
    void getStockPortfolio_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/stock/portfolio"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("getStockPortfolio는 서비스가 만든 포트폴리오를 그대로 반환한다")
    void getStockPortfolio_authenticated_returnsPortfolio() throws Exception {
        StockPortfolioResponse portfolio = StockPortfolioResponse.builder()
                .totalMarketValue(new BigDecimal("750000"))
                .totalProfitLoss(new BigDecimal("50000"))
                .totalProfitLossRate(new BigDecimal("7.14"))
                .holdings(List.of())
                .lastUpdated("2026-07-19T00:00:00")
                .build();
        given(stockService.getStockPortfolio()).willReturn(portfolio);

        mockMvc.perform(get("/api/stock/portfolio").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalMarketValue", is(750000)));
    }

    @Test
    @DisplayName("getTradingProfitLoss는 요청 바디를 그대로 서비스에 전달해 응답한다")
    void getTradingProfitLoss_delegatesRequestToService() throws Exception {
        TradingProfitLossRequest request = new TradingProfitLossRequest();
        request.setStartDate("2026-07-01");
        request.setEndDate("2026-07-19");
        request.setPeriodType("CUSTOM");

        TradingProfitLossResponse response = TradingProfitLossResponse.builder()
                .period("2026-07-01 ~ 2026-07-19")
                .totalBuyAmount(BigDecimal.ZERO)
                .totalSellAmount(BigDecimal.ZERO)
                .totalProfitLoss(BigDecimal.ZERO)
                .totalProfitLossRate(BigDecimal.ZERO)
                .totalFee(BigDecimal.ZERO)
                .totalTax(BigDecimal.ZERO)
                .tradeCount(0)
                .trades(List.of())
                .build();
        given(stockService.getTradingProfitLoss(org.mockito.ArgumentMatchers.any(TradingProfitLossRequest.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/stock/trading-profit-loss")
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period", is("2026-07-01 ~ 2026-07-19")));
    }
}
