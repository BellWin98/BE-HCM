package com.behcm.domain.stock.service;

import com.behcm.domain.stock.dto.StockInfoResponse;
import com.behcm.domain.stock.dto.StockPortfolioResponse;
import com.behcm.domain.stock.dto.StockPriceResponse;
import com.behcm.global.config.stock.KoreaInvestmentClient;
import com.behcm.global.config.stock.KoreaInvestmentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final KoreaInvestmentClient koreaInvestmentClient;
    private final KoreaInvestmentProperties properties;

    public StockPortfolioResponse getStockPortfolio() {
        Map<String, String> params = new HashMap<>();
        params.put("CANO", properties.getAccountNumber());
        params.put("ACNT_PRDT_CD", properties.getAccountProductCode());
        params.put("AFHR_FLPR_YN", "N");
        params.put("OFL_YN", "");
        params.put("INQR_DVSN", "02");
        params.put("UNPR_DVSN", "01");
        params.put("FUND_STTL_ICLD_YN", "N");
        params.put("FNCG_AMT_AUTO_RDPT_YN", "N");
        params.put("PRCS_DVSN", "01");
        params.put("CTX_AREA_FK100", "");
        params.put("CTX_AREA_NK100", "");

        JsonNode response = koreaInvestmentClient.callApiWithParams(
            "/uapi/domestic-stock/v1/trading/inquire-balance",
            "TTTC8434R",
            params
        );

        return parsePortfolioResponse(response);
    }

    public StockPriceResponse getStockPrice(String stockCode) {
        Map<String, String> params = new HashMap<>();
        params.put("FID_COND_MRKT_DIV_CODE", "J");
        params.put("FID_INPUT_ISCD", stockCode);

        JsonNode response = koreaInvestmentClient.callApiWithParams(
            "/uapi/domestic-stock/v1/quotations/inquire-price",
            "FHKST01010100",
            params
        );

        return parseStockPriceResponse(response, stockCode);
    }

    public StockInfoResponse getStockInfo(String stockCode) {
        Map<String, String> params = new HashMap<>();
        params.put("FID_COND_MRKT_DIV_CODE", "J");
        params.put("FID_INPUT_ISCD", stockCode);

        JsonNode response = koreaInvestmentClient.callApiWithParams(
            "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice",
            "FHKST03010100",
            params
        );

        return parseStockInfoResponse(response, stockCode);
    }

    public void refreshStockData() {
        log.info("Stock data refresh requested");
    }

    private StockPortfolioResponse parsePortfolioResponse(JsonNode response) {
        JsonNode output1 = response.get("output1");
        if (output1 == null || output1.isEmpty()) {
            return StockPortfolioResponse.builder()
                .totalMarketValue(BigDecimal.ZERO)
                .totalProfitLoss(BigDecimal.ZERO)
                .totalProfitLossRate(BigDecimal.ZERO)
                .holdings(new ArrayList<>())
                .lastUpdated(java.time.LocalDateTime.now().toString())
                .build();
        }

        JsonNode output2 = response.get("output2");
        List<StockPortfolioResponse.StockHoldingDto> holdings = new ArrayList<>();

        if (output1.isArray()) {
            for (JsonNode holding : output1) {
                if (holding.get("hldg_qty") != null &&
                    new BigDecimal(holding.get("hldg_qty").asText()).compareTo(BigDecimal.ZERO) > 0) {

                    StockPortfolioResponse.StockHoldingDto holdingDto = StockPortfolioResponse.StockHoldingDto.builder()
                        .stockCode(holding.get("pdno").asText())
                        .stockName(holding.get("prdt_name").asText())
                        .quantity(Integer.parseInt(holding.get("hldg_qty").asText()))
                        .averagePrice(new BigDecimal(holding.get("pchs_avg_pric").asText()))
                        .currentPrice(new BigDecimal(holding.get("prpr").asText()))
                        .marketValue(new BigDecimal(holding.get("evlu_amt").asText()))
                        .purchasePrice(new BigDecimal(holding.get("pchs_amt").asText()))
                        .profitLoss(new BigDecimal(holding.get("evlu_pfls_amt").asText()))
                        .profitLossRate(new BigDecimal(holding.get("evlu_pfls_rt").asText()))
                        .sector("")
                        .build();

                    holdings.add(holdingDto);
                }
            }
        }

        BigDecimal totalMarketValue = output2 != null ? new BigDecimal(output2.get(0).get("evlu_amt_smtl_amt").asText()) : BigDecimal.ZERO;
        BigDecimal totalBuyValue = output2 != null ? new BigDecimal(output2.get(0).get("pchs_amt_smtl_amt").asText()) : BigDecimal.ZERO;
        BigDecimal totalProfitLoss = output2 != null ? new BigDecimal(output2.get(0).get("evlu_pfls_smtl_amt").asText()) : BigDecimal.ZERO;
        BigDecimal totalProfitLossRate = output2 != null ? new BigDecimal(String.valueOf(totalMarketValue.subtract(totalBuyValue).divide(totalBuyValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)))) : BigDecimal.ZERO;
        BigDecimal depositToday = output2 != null ? new BigDecimal(output2.get(0).get("dnca_tot_amt").asText()) : BigDecimal.ZERO;
        BigDecimal depositD2 = output2 != null ? new BigDecimal(output2.get(0).get("prvs_rcdl_excc_amt").asText()) : BigDecimal.ZERO;

        return StockPortfolioResponse.builder()
            .totalBuyValue(totalBuyValue)
            .totalMarketValue(totalMarketValue)
            .totalProfitLoss(totalProfitLoss)
            .totalProfitLossRate(totalProfitLossRate)
            .depositToday(depositToday)
            .depositD2(depositD2)
            .holdings(holdings)
            .lastUpdated(java.time.LocalDateTime.now().toString())
            .build();
    }

    private StockPriceResponse parseStockPriceResponse(JsonNode response, String stockCode) {
        JsonNode output = response.get("output");
        if (output == null) {
            throw new RuntimeException("Failed to get stock price data");
        }

        return StockPriceResponse.builder()
            .stockCode(stockCode)
            .stockName(output.get("hts_kor_isnm").asText())
            .currentPrice(new BigDecimal(output.get("stck_prpr").asText()))
            .changeAmount(new BigDecimal(output.get("prdy_vrss").asText()))
            .changeRate(new BigDecimal(output.get("prdy_ctrt").asText()))
            .changeDirection(output.get("prdy_vrss_sign").asText())
            .volume(new BigDecimal(output.get("acml_vol").asText()))
            .highPrice(new BigDecimal(output.get("stck_hgpr").asText()))
            .lowPrice(new BigDecimal(output.get("stck_lwpr").asText()))
            .openPrice(new BigDecimal(output.get("stck_oprc").asText()))
            .marketType(output.get("mrkt_ctg").asText())
            .build();
    }

    private StockInfoResponse parseStockInfoResponse(JsonNode response, String stockCode) {
        JsonNode output = response.get("output");
        if (output == null || output.isEmpty() || !output.isArray()) {
            throw new RuntimeException("Failed to get stock info data");
        }

        JsonNode stockData = output.get(0);

        return StockInfoResponse.builder()
            .stockCode(stockCode)
            .stockName(stockData.get("hts_kor_isnm") != null ? stockData.get("hts_kor_isnm").asText() : "")
            .marketType(stockData.get("mrkt_ctg") != null ? stockData.get("mrkt_ctg").asText() : "")
            .sector("")
            .marketCap(BigDecimal.ZERO)
            .per(BigDecimal.ZERO)
            .pbr(BigDecimal.ZERO)
            .eps(BigDecimal.ZERO)
            .bps(BigDecimal.ZERO)
            .listedDate("")
            .listedShares(0L)
            .description("")
            .build();
    }
}