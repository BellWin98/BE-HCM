package com.behcm.domain.stock.service;

import com.behcm.domain.stock.dto.StockInfoResponse;
import com.behcm.domain.stock.dto.StockPortfolioResponse;
import com.behcm.domain.stock.dto.TradingProfitLossRequest;
import com.behcm.domain.stock.dto.TradingProfitLossResponse;
import com.behcm.global.config.stock.KoreaInvestmentClient;
import com.behcm.global.config.stock.KoreaInvestmentProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    private static final String PORTFOLIO_ENDPOINT = "/uapi/domestic-stock/v1/trading/inquire-balance";
    private static final String PORTFOLIO_TR_ID = "TTTC8434R";
    private static final String STOCK_INFO_ENDPOINT = "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice";
    private static final String STOCK_INFO_TR_ID = "FHKST03010100";
    private static final String PRICE_ENDPOINT = "/uapi/domestic-stock/v1/quotations/inquire-price";
    private static final String PRICE_TR_ID = "FHKST01010100";
    private static final String PROFIT_LOSS_ENDPOINT = "/uapi/domestic-stock/v1/trading/inquire-period-trade-profit";
    private static final String PROFIT_LOSS_TR_ID = "TTTC8715R";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private KoreaInvestmentClient koreaInvestmentClient;

    @Mock
    private KoreaInvestmentProperties properties;

    @InjectMocks
    private StockService stockService;

    private JsonNode json(String jsonText) {
        try {
            return objectMapper.readTree(jsonText);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------- getStockPortfolio ----------

    @Test
    @DisplayName("보유 종목이 없으면(output1이 빈 배열) 0으로 채워진 포트폴리오를 반환한다")
    void getStockPortfolio_emptyHoldings_returnsZeroFilledResponse() {
        JsonNode response = json("""
                { "output1": [] }
                """);
        given(koreaInvestmentClient.callApiWithParams(eq(PORTFOLIO_ENDPOINT), eq(PORTFOLIO_TR_ID), any()))
                .willReturn(response);

        StockPortfolioResponse result = stockService.getStockPortfolio();

        assertThat(result.getHoldings()).isEmpty();
        assertThat(result.getTotalMarketValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotalProfitLoss()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("보유수량이 0인 종목은 결과에서 제외되고, 나머지 종목은 정상 파싱된다")
    void getStockPortfolio_filtersZeroQuantityHoldings() {
        JsonNode response = json("""
                {
                  "output1": [
                    { "pdno": "005930", "prdt_name": "삼성전자", "hldg_qty": "10",
                      "pchs_avg_pric": "70000", "prpr": "75000", "evlu_amt": "750000",
                      "pchs_amt": "700000", "evlu_pfls_amt": "50000", "evlu_pfls_rt": "7.14" },
                    { "pdno": "000660", "prdt_name": "SK하이닉스", "hldg_qty": "0",
                      "pchs_avg_pric": "0", "prpr": "0", "evlu_amt": "0",
                      "pchs_amt": "0", "evlu_pfls_amt": "0", "evlu_pfls_rt": "0" }
                  ],
                  "output2": [
                    { "evlu_amt_smtl_amt": "750000", "pchs_amt_smtl_amt": "700000",
                      "evlu_pfls_smtl_amt": "50000", "dnca_tot_amt": "100000",
                      "prvs_rcdl_excc_amt": "90000" }
                  ]
                }
                """);
        given(koreaInvestmentClient.callApiWithParams(eq(PORTFOLIO_ENDPOINT), eq(PORTFOLIO_TR_ID), any()))
                .willReturn(response);
        JsonNode priceResponse = json("""
                { "output": { "prdy_ctrt": "1.23" } }
                """);
        given(koreaInvestmentClient.callApiWithParams(eq(PRICE_ENDPOINT), eq(PRICE_TR_ID), any()))
                .willReturn(priceResponse);

        StockPortfolioResponse result = stockService.getStockPortfolio();

        assertThat(result.getHoldings()).hasSize(1);
        StockPortfolioResponse.StockHoldingDto holding = result.getHoldings().get(0);
        assertThat(holding.getStockCode()).isEqualTo("005930");
        assertThat(holding.getQuantity()).isEqualTo(10);
        assertThat(holding.getDayChangeRate()).isEqualByComparingTo(new BigDecimal("1.2"));
        assertThat(result.getTotalMarketValue()).isEqualByComparingTo(new BigDecimal("750000"));
        assertThat(result.getTotalBuyValue()).isEqualByComparingTo(new BigDecimal("700000"));
        assertThat(result.getDepositToday()).isEqualByComparingTo(new BigDecimal("100000"));
    }

    // ---------- getStockInfo ----------

    @Test
    @DisplayName("getStockInfo는 output 배열의 첫 종목 정보를 파싱해 반환한다")
    void getStockInfo_success_parsesFirstOutputEntry() {
        JsonNode response = json("""
                { "output": [ { "hts_kor_isnm": "삼성전자", "mrkt_ctg": "KOSPI" } ] }
                """);
        given(koreaInvestmentClient.callApiWithParams(eq(STOCK_INFO_ENDPOINT), eq(STOCK_INFO_TR_ID), any()))
                .willReturn(response);

        StockInfoResponse result = stockService.getStockInfo("005930");

        assertThat(result.getStockCode()).isEqualTo("005930");
        assertThat(result.getStockName()).isEqualTo("삼성전자");
        assertThat(result.getMarketType()).isEqualTo("KOSPI");
    }

    @Test
    @DisplayName("getStockInfo는 output이 비어있으면 RuntimeException을 던진다")
    void getStockInfo_emptyOutput_throwsRuntimeException() {
        JsonNode response = json("""
                { "output": [] }
                """);
        given(koreaInvestmentClient.callApiWithParams(eq(STOCK_INFO_ENDPOINT), eq(STOCK_INFO_TR_ID), any()))
                .willReturn(response);

        assertThatThrownBy(() -> stockService.getStockInfo("005930"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to get stock info data");
    }

    // ---------- getTradingProfitLoss ----------

    private TradingProfitLossRequest profitLossRequest() {
        TradingProfitLossRequest request = new TradingProfitLossRequest();
        request.setStartDate("2026-07-01");
        request.setEndDate("2026-07-19");
        request.setPeriodType("CUSTOM");
        return request;
    }

    @Test
    @DisplayName("단일 페이지 응답에서 매수/매도 내역을 분리해 거래일 내림차순으로 정렬한다")
    void getTradingProfitLoss_singlePage_parsesAndSortsTrades() {
        JsonNode response = json("""
                {
                  "output1": [
                    { "pdno": "005930", "prdt_name": "삼성전자", "trad_dt": "20260701",
                      "buy_qty": "10", "pchs_unpr": "70000", "buy_amt": "700000", "fee": "100" },
                    { "pdno": "000660", "prdt_name": "SK하이닉스", "trad_dt": "20260715",
                      "sll_qty": "5", "sll_pric": "150000", "sll_amt": "750000",
                      "rlzt_pfls": "50000", "pfls_rt": "7.14", "fee": "200", "tl_tax": "300" }
                  ],
                  "output2": {
                    "buy_excc_amt_smtl": "700000", "sll_excc_amt_smtl": "750000",
                    "tot_rlzt_pfls": "50000", "tot_pftrt": "7.14",
                    "tot_fee": "300", "tot_tltx": "300"
                  },
                  "ctx_area_nk100": "",
                  "ctx_area_fk100": ""
                }
                """);
        given(koreaInvestmentClient.callApiWithParams(eq(PROFIT_LOSS_ENDPOINT), eq(PROFIT_LOSS_TR_ID), any(), any()))
                .willReturn(response);

        TradingProfitLossResponse result = stockService.getTradingProfitLoss(profitLossRequest());

        assertThat(result.getPeriod()).isEqualTo("2026-07-01 ~ 2026-07-19");
        assertThat(result.getTradeCount()).isEqualTo(2);
        assertThat(result.getTrades()).extracting(TradingProfitLossResponse.TradingProfitLossDto::getTradeDate)
                .containsExactly("2026-07-15", "2026-07-01"); // 최신순 정렬
        assertThat(result.getTrades()).extracting(TradingProfitLossResponse.TradingProfitLossDto::getTradeType)
                .containsExactly("SELL", "BUY");
        assertThat(result.getTotalProfitLoss()).isEqualByComparingTo(new BigDecimal("50000"));
        verify(koreaInvestmentClient, times(1))
                .callApiWithParams(eq(PROFIT_LOSS_ENDPOINT), eq(PROFIT_LOSS_TR_ID), any(), any());
    }

    @Test
    @DisplayName("buy_qty/sll_qty가 0인 항목은 거래 내역에 포함되지 않는다")
    void getTradingProfitLoss_zeroQuantityTrades_areExcluded() {
        JsonNode response = json("""
                {
                  "output1": [
                    { "pdno": "005930", "prdt_name": "삼성전자", "trad_dt": "20260701",
                      "buy_qty": "0", "sll_qty": "0" }
                  ],
                  "output2": {},
                  "ctx_area_nk100": "",
                  "ctx_area_fk100": ""
                }
                """);
        given(koreaInvestmentClient.callApiWithParams(eq(PROFIT_LOSS_ENDPOINT), eq(PROFIT_LOSS_TR_ID), any(), any()))
                .willReturn(response);

        TradingProfitLossResponse result = stockService.getTradingProfitLoss(profitLossRequest());

        assertThat(result.getTrades()).isEmpty();
        assertThat(result.getTradeCount()).isZero();
    }

    @Test
    @DisplayName("ctx_area 키가 비어있지 않으면 다음 페이지를 연속키/tr_cont=N 헤더로 재조회해 결과를 누적한다")
    void getTradingProfitLoss_pagination_accumulatesAcrossPages() {
        JsonNode page1 = json("""
                {
                  "output1": [
                    { "pdno": "005930", "prdt_name": "삼성전자", "trad_dt": "20260701",
                      "buy_qty": "10", "pchs_unpr": "70000", "buy_amt": "700000", "fee": "0" }
                  ],
                  "output2": {},
                  "ctx_area_nk100": "NEXT_KEY",
                  "ctx_area_fk100": "NEXT_KEY2"
                }
                """);
        JsonNode page2 = json("""
                {
                  "output1": [
                    { "pdno": "000660", "prdt_name": "SK하이닉스", "trad_dt": "20260710",
                      "buy_qty": "3", "pchs_unpr": "150000", "buy_amt": "450000", "fee": "0" }
                  ],
                  "output2": { "buy_excc_amt_smtl": "1150000" },
                  "ctx_area_nk100": "",
                  "ctx_area_fk100": ""
                }
                """);
        // headers 맵은 루프 내에서 재사용되며 매 호출 후 in-place로 mutate되므로,
        // ArgumentCaptor로는 호출 시점 스냅샷을 얻을 수 없다 -> willAnswer에서 즉시 복사해 기록한다.
        List<Map<String, String>> capturedHeaderSnapshots = new java.util.ArrayList<>();
        given(koreaInvestmentClient.callApiWithParams(eq(PROFIT_LOSS_ENDPOINT), eq(PROFIT_LOSS_TR_ID), any(), any()))
                .willAnswer(invocation -> {
                    Map<String, String> headersArg = invocation.getArgument(3);
                    capturedHeaderSnapshots.add(new java.util.HashMap<>(headersArg));
                    return capturedHeaderSnapshots.size() == 1 ? page1 : page2;
                });

        TradingProfitLossResponse result = stockService.getTradingProfitLoss(profitLossRequest());

        assertThat(result.getTradeCount()).isEqualTo(2);
        assertThat(result.getTotalBuyAmount()).isEqualByComparingTo(new BigDecimal("1150000"));

        verify(koreaInvestmentClient, times(2))
                .callApiWithParams(eq(PROFIT_LOSS_ENDPOINT), eq(PROFIT_LOSS_TR_ID), any(), any());
        assertThat(capturedHeaderSnapshots).hasSize(2);
        assertThat(capturedHeaderSnapshots.get(0).get("tr_cont")).isEqualTo("");
        assertThat(capturedHeaderSnapshots.get(1).get("tr_cont")).isEqualTo("N");
    }
}
