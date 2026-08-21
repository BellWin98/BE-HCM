package com.behcm.domain.stock.service;

import com.behcm.domain.stock.dto.StockInfoResponse;
import com.behcm.domain.stock.dto.StockPortfolioResponse;
import com.behcm.domain.stock.dto.TradingProfitLossRequest;
import com.behcm.domain.stock.dto.TradingProfitLossResponse;
import com.behcm.global.config.stock.KoreaInvestmentClient;
import com.behcm.global.config.stock.KoreaInvestmentProperties;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
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

    @Test
    @DisplayName("매입금액 합계가 0이면 0으로 나누지 않고 수익률 0을 반환한다")
    void getStockPortfolio_zeroBuyValue_returnsZeroRateWithoutArithmeticException() {
        // 보유수량은 있으나 매입금액 합계가 0인 계좌(무상증자·대용주 등)에서도 터지면 안 된다.
        JsonNode response = json("""
                {
                  "output1": [
                    { "pdno": "005930", "prdt_name": "삼성전자", "hldg_qty": "10",
                      "pchs_avg_pric": "0", "prpr": "75000", "evlu_amt": "750000",
                      "pchs_amt": "0", "evlu_pfls_amt": "750000", "evlu_pfls_rt": "0" }
                  ],
                  "output2": [
                    { "evlu_amt_smtl_amt": "750000", "pchs_amt_smtl_amt": "0",
                      "evlu_pfls_smtl_amt": "750000", "dnca_tot_amt": "0",
                      "prvs_rcdl_excc_amt": "0" }
                  ]
                }
                """);
        given(koreaInvestmentClient.callApiWithParams(eq(PORTFOLIO_ENDPOINT), eq(PORTFOLIO_TR_ID), any()))
                .willReturn(response);
        given(koreaInvestmentClient.callApiWithParams(eq(PRICE_ENDPOINT), eq(PRICE_TR_ID), any()))
                .willReturn(json("""
                        { "output": { "prdy_ctrt": "1.23" } }
                        """));

        StockPortfolioResponse result = stockService.getStockPortfolio();

        assertThat(result.getTotalProfitLossRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotalMarketValue()).isEqualByComparingTo(new BigDecimal("750000"));
    }

    @Test
    @DisplayName("output2가 빈 배열이어도 NPE 없이 합계 0으로 채운다")
    void getStockPortfolio_emptyOutput2_doesNotThrow() {
        JsonNode response = json("""
                {
                  "output1": [
                    { "pdno": "005930", "prdt_name": "삼성전자", "hldg_qty": "10",
                      "pchs_avg_pric": "70000", "prpr": "75000", "evlu_amt": "750000",
                      "pchs_amt": "700000", "evlu_pfls_amt": "50000", "evlu_pfls_rt": "7.14" }
                  ],
                  "output2": []
                }
                """);
        given(koreaInvestmentClient.callApiWithParams(eq(PORTFOLIO_ENDPOINT), eq(PORTFOLIO_TR_ID), any()))
                .willReturn(response);
        given(koreaInvestmentClient.callApiWithParams(eq(PRICE_ENDPOINT), eq(PRICE_TR_ID), any()))
                .willReturn(json("""
                        { "output": { "prdy_ctrt": "1.23" } }
                        """));

        StockPortfolioResponse result = stockService.getStockPortfolio();

        assertThat(result.getHoldings()).hasSize(1);
        assertThat(result.getTotalMarketValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotalBuyValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotalProfitLossRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("숫자가 아닌 빈 문자열 필드가 와도 0으로 처리한다")
    void getStockPortfolio_blankNumericFields_treatedAsZero() {
        JsonNode response = json("""
                {
                  "output1": [
                    { "pdno": "005930", "prdt_name": "삼성전자", "hldg_qty": "10",
                      "pchs_avg_pric": "", "prpr": "75000", "evlu_amt": "750000",
                      "pchs_amt": "700000", "evlu_pfls_amt": "50000", "evlu_pfls_rt": "" }
                  ],
                  "output2": [
                    { "evlu_amt_smtl_amt": "750000", "pchs_amt_smtl_amt": "700000",
                      "evlu_pfls_smtl_amt": "50000", "dnca_tot_amt": "", "prvs_rcdl_excc_amt": "" }
                  ]
                }
                """);
        given(koreaInvestmentClient.callApiWithParams(eq(PORTFOLIO_ENDPOINT), eq(PORTFOLIO_TR_ID), any()))
                .willReturn(response);
        given(koreaInvestmentClient.callApiWithParams(eq(PRICE_ENDPOINT), eq(PRICE_TR_ID), any()))
                .willReturn(json("""
                        { "output": { "prdy_ctrt": "1.23" } }
                        """));

        StockPortfolioResponse result = stockService.getStockPortfolio();

        assertThat(result.getHoldings().get(0).getAveragePrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getDepositToday()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("동일 종목코드가 두 번 보유되어 있어도 각 항목 모두 전일대비 변동률이 채워진다")
    void getStockPortfolio_duplicateStockCodes_allEntriesGetDayChangeRate() {
        // 종목코드를 Map 키로 쓰면 뒤 항목이 앞 항목을 덮어써 하나가 null 로 남는다.
        JsonNode response = json("""
                {
                  "output1": [
                    { "pdno": "005930", "prdt_name": "삼성전자", "hldg_qty": "10",
                      "pchs_avg_pric": "70000", "prpr": "75000", "evlu_amt": "750000",
                      "pchs_amt": "700000", "evlu_pfls_amt": "50000", "evlu_pfls_rt": "7.14" },
                    { "pdno": "005930", "prdt_name": "삼성전자", "hldg_qty": "5",
                      "pchs_avg_pric": "72000", "prpr": "75000", "evlu_amt": "375000",
                      "pchs_amt": "360000", "evlu_pfls_amt": "15000", "evlu_pfls_rt": "4.16" }
                  ],
                  "output2": [
                    { "evlu_amt_smtl_amt": "1125000", "pchs_amt_smtl_amt": "1060000",
                      "evlu_pfls_smtl_amt": "65000", "dnca_tot_amt": "0", "prvs_rcdl_excc_amt": "0" }
                  ]
                }
                """);
        given(koreaInvestmentClient.callApiWithParams(eq(PORTFOLIO_ENDPOINT), eq(PORTFOLIO_TR_ID), any()))
                .willReturn(response);
        given(koreaInvestmentClient.callApiWithParams(eq(PRICE_ENDPOINT), eq(PRICE_TR_ID), any()))
                .willReturn(json("""
                        { "output": { "prdy_ctrt": "1.23" } }
                        """));

        StockPortfolioResponse result = stockService.getStockPortfolio();

        assertThat(result.getHoldings()).hasSize(2);
        assertThat(result.getHoldings())
                .extracting(StockPortfolioResponse.StockHoldingDto::getDayChangeRate)
                .allSatisfy(rate -> assertThat(rate).isEqualByComparingTo(new BigDecimal("1.2")));
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
    @DisplayName("매수 단가는 평균단가(pchs_unpr)가 아니라 매수금액/매수수량으로 계산한다")
    void getTradingProfitLoss_buyPrice_derivedFromAmountOverQuantity() {
        // pchs_unpr 은 그날 체결가가 아니라 보유 포지션의 평균 매입단가라서,
        // 그대로 쓰면 거래 내역에 평균단가가 찍힌다. 금액/수량이 실제 체결 평균가다.
        JsonNode response = json("""
                {
                  "output1": [
                    { "pdno": "005930", "prdt_name": "삼성전자", "trad_dt": "20260701",
                      "buy_qty": "10", "pchs_unpr": "65000", "buy_amt": "700000", "fee": "100" }
                  ],
                  "output2": {},
                  "ctx_area_nk100": "",
                  "ctx_area_fk100": ""
                }
                """);
        given(koreaInvestmentClient.callApiWithParams(eq(PROFIT_LOSS_ENDPOINT), eq(PROFIT_LOSS_TR_ID), any(), any()))
                .willReturn(response);

        TradingProfitLossResponse result = stockService.getTradingProfitLoss(profitLossRequest());

        assertThat(result.getTrades()).hasSize(1);
        assertThat(result.getTrades().get(0).getPrice()).isEqualByComparingTo(new BigDecimal("70000"));
    }

    @Test
    @DisplayName("매도 단가도 매도금액/매도수량으로 계산한다")
    void getTradingProfitLoss_sellPrice_derivedFromAmountOverQuantity() {
        JsonNode response = json("""
                {
                  "output1": [
                    { "pdno": "000660", "prdt_name": "SK하이닉스", "trad_dt": "20260715",
                      "sll_qty": "5", "sll_pric": "140000", "sll_amt": "750000",
                      "rlzt_pfls": "50000", "pfls_rt": "7.14", "fee": "200", "tl_tax": "300" }
                  ],
                  "output2": {},
                  "ctx_area_nk100": "",
                  "ctx_area_fk100": ""
                }
                """);
        given(koreaInvestmentClient.callApiWithParams(eq(PROFIT_LOSS_ENDPOINT), eq(PROFIT_LOSS_TR_ID), any(), any()))
                .willReturn(response);

        TradingProfitLossResponse result = stockService.getTradingProfitLoss(profitLossRequest());

        assertThat(result.getTrades().get(0).getPrice()).isEqualByComparingTo(new BigDecimal("150000"));
    }

    @Test
    @DisplayName("금액이 없으면 API가 준 단가 필드로 대체한다")
    void getTradingProfitLoss_missingAmount_fallsBackToApiUnitPrice() {
        JsonNode response = json("""
                {
                  "output1": [
                    { "pdno": "005930", "prdt_name": "삼성전자", "trad_dt": "20260701",
                      "buy_qty": "10", "pchs_unpr": "65000", "buy_amt": "0", "fee": "0" }
                  ],
                  "output2": {},
                  "ctx_area_nk100": "",
                  "ctx_area_fk100": ""
                }
                """);
        given(koreaInvestmentClient.callApiWithParams(eq(PROFIT_LOSS_ENDPOINT), eq(PROFIT_LOSS_TR_ID), any(), any()))
                .willReturn(response);

        TradingProfitLossResponse result = stockService.getTradingProfitLoss(profitLossRequest());

        assertThat(result.getTrades().get(0).getPrice()).isEqualByComparingTo(new BigDecimal("65000"));
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
    @DisplayName("연속키가 계속 반환되어도 최대 페이지 수에서 조회를 중단한다")
    void getTradingProfitLoss_neverEndingCursor_stopsAtMaxPages() {
        // 연속키가 매번 동일하게 내려오는 상황(무한 루프)에서도 호출이 유한해야 한다.
        JsonNode endlessPage = json("""
                {
                  "output1": [],
                  "output2": {},
                  "ctx_area_nk100": "SAME_KEY",
                  "ctx_area_fk100": "SAME_KEY2"
                }
                """);
        given(koreaInvestmentClient.callApiWithParams(eq(PROFIT_LOSS_ENDPOINT), eq(PROFIT_LOSS_TR_ID), any(), any()))
                .willReturn(endlessPage);

        TradingProfitLossResponse result = stockService.getTradingProfitLoss(profitLossRequest());

        assertThat(result.getTrades()).isEmpty();
        verify(koreaInvestmentClient, times(StockService.MAX_PROFIT_LOSS_PAGES))
                .callApiWithParams(eq(PROFIT_LOSS_ENDPOINT), eq(PROFIT_LOSS_TR_ID), any(), any());
    }

    @Test
    @DisplayName("startDate가 null이면 NPE 대신 INVALID_INPUT CustomException을 던진다")
    void getTradingProfitLoss_nullStartDate_throwsCustomException() {
        TradingProfitLossRequest request = new TradingProfitLossRequest();
        request.setEndDate("2026-07-19");
        request.setPeriodType("CUSTOM");

        assertThatThrownBy(() -> stockService.getTradingProfitLoss(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 INVALID_INPUT CustomException을 던진다")
    void getTradingProfitLoss_startAfterEnd_throwsCustomException() {
        TradingProfitLossRequest request = new TradingProfitLossRequest();
        request.setStartDate("2026-07-20");
        request.setEndDate("2026-07-01");
        request.setPeriodType("CUSTOM");

        assertThatThrownBy(() -> stockService.getTradingProfitLoss(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("날짜 형식이 yyyy-MM-dd가 아니면 INVALID_INPUT CustomException을 던진다")
    void getTradingProfitLoss_malformedDate_throwsCustomException() {
        TradingProfitLossRequest request = new TradingProfitLossRequest();
        request.setStartDate("2026/07/01");
        request.setEndDate("2026-07-19");
        request.setPeriodType("CUSTOM");

        assertThatThrownBy(() -> stockService.getTradingProfitLoss(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
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
