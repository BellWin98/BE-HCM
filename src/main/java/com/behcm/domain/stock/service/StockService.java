package com.behcm.domain.stock.service;

import com.behcm.domain.stock.dto.*;
import com.behcm.domain.stock.dto.TradingProfitLossResponse.TradingProfitLossDto;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final KoreaInvestmentClient koreaInvestmentClient;
    private final KoreaInvestmentProperties properties;
    
    // API 호출 제한: 초당 20개
    private static final int API_CALLS_PER_SECOND = 20;
    private static final long THROTTLE_DELAY_MS = 1000 / API_CALLS_PER_SECOND; // 50ms
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public StockPortfolioResponse getStockPortfolio() {
        Map<String, String> params = new HashMap<>();
        params.put("CANO", properties.getAccountNumber());
        params.put("ACNT_PRDT_CD", properties.getAccountProductCode());
        params.put("AFHR_FLPR_YN", "N");
        params.put("OFL_YN", "");
        params.put("INQR_DVSN", "01");
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

    public TradingProfitLossResponse getTradingProfitLoss(TradingProfitLossRequest request) {
        log.debug("Trading profit loss request - startDate: {}, endDate: {}, periodType: {}", 
                request.getStartDate(), request.getEndDate(), request.getPeriodType());
        
        // 1. 요청 파라미터 (Query Params) 설정
        Map<String, String> params = new HashMap<>();
        params.put("CANO", properties.getAccountNumber());
        params.put("ACNT_PRDT_CD", properties.getAccountProductCode());
        params.put("PDNO", "");
        params.put("PRDT_TYPE_CD", "");
        params.put("SMRT_INQRY_DVSN_CD", "01"); // 01: 전체
        params.put("PD_DVSN_CD", "01");
        params.put("SORT_DVSN", "00"); // 00: 상품번호순
        params.put("INQR_STRT_DT", request.getStartDate().replace("-", ""));
        params.put("INQR_END_DT", request.getEndDate().replace("-", ""));
        params.put("CBLC_DVSN", "00");

        // 초기 연속키는 빈 값으로 시작
        params.put("CTX_AREA_FK100", "");
        params.put("CTX_AREA_NK100", "");
        // 2. 요청 헤더 (Headers) 설정용 Map
        // 초기 조회 시 tr_cont는 빈 값 (또는 생략)
        Map<String, String> headers = new HashMap<>();
        headers.put("tr_cont", "");
        List<TradingProfitLossDto> allTrades = new ArrayList<>();
        JsonNode lastResponse = null;
        // 3. 반복 조회 루프 (Pagination)
        while (true) {
            // API 호출 (headers 맵을 추가로 전달한다고 가정)
            // Client의 callApiWithParams 메서드가 헤더를 받을 수 있도록 수정되어야 합니다.
            JsonNode response = koreaInvestmentClient.callApiWithParams(
                    "/uapi/domestic-stock/v1/trading/inquire-period-trade-profit",
                    "TTTC8715R",
                    params,
                    headers
            );

            lastResponse = response; // 마지막 응답 저장 (output2 집계 데이터용)
            // 4. output1 (개별 내역) 파싱 및 전체 리스트에 누적
            JsonNode output1 = response.get("output1");
            if (output1 != null && output1.isArray()) {
                for (JsonNode trade : output1) {
                    // 매수/매도 구분 로직 (buy_qty가 0이면 매도, 아니면 매수)
                    boolean isBuy = !trade.path("buy_qty").asText("0").equals("0");

                    TradingProfitLossDto tradeDto = TradingProfitLossDto.builder()
                            .stockCode(trade.path("pdno").asText())
                            .stockName(trade.path("prdt_name").asText())
                            .tradeDate(formatTradeDate(trade.path("trad_dt").asText()))
                            .tradeType(isBuy ? "BUY" : "SELL")
                            .quantity(Integer.parseInt(isBuy ? trade.path("buy_qty").asText("0") : trade.path("sll_qty").asText("0")))
                            .price(new BigDecimal(isBuy ? trade.path("pchs_unpr").asText("0") : trade.path("sll_pric").asText("0")))
                            .amount(new BigDecimal(isBuy ? trade.path("buy_amt").asText("0") : trade.path("sll_amt").asText("0")))
                            .profitLoss(new BigDecimal(trade.path("rlzt_pfls").asText("0")))
                            .profitLossRate(new BigDecimal(trade.path("pfls_rt").asText("0")))
                            .fee(new BigDecimal(trade.path("fee").asText("0")))
                            .tax(new BigDecimal(trade.path("tl_tax").asText("0"))) // null safe 처리
                            .build();
                    allTrades.add(tradeDto);
                }
            }
            // 5. 연속 데이터 확인 (Response Body의 ctx 값 확인)
            String ctxAreaNk = response.path("ctx_area_nk100").asText().trim();
            String ctxAreaFk = response.path("ctx_area_fk100").asText().trim();
            // 다음 데이터가 없으면(키 값이 비어있으면) 루프 종료
            if (ctxAreaNk.isEmpty() && ctxAreaFk.isEmpty()) {
                break;
            }
            // 6. 다음 페이지 조회를 위한 파라미터 및 헤더 업데이트
            // 파라미터에 연속 키 세팅
            params.put("CTX_AREA_NK100", ctxAreaNk);
            params.put("CTX_AREA_FK100", ctxAreaFk);

            // **중요: 다음 조회 시 헤더에 tr_cont = "N" 설정**
            headers.put("tr_cont", "N");
            // (선택사항) API 호출 제한 고려하여 짧은 대기 시간 추가
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // 7. 거래 내역 날짜순 정렬 (최신순)
        allTrades.sort((a, b) -> b.getTradeDate().compareTo(a.getTradeDate()));
        
        // 8. 최종 결과 생성 (output2 합계 데이터 및 전체 리스트 반환)
        return parseTradingProfitLossResponse(lastResponse, request, allTrades);
    }

    private TradingProfitLossResponse parseTradingProfitLossResponse(JsonNode response, TradingProfitLossRequest request, List<TradingProfitLossDto> allTrades) {
        JsonNode output2 = (response != null && response.has("output2")) ? response.get("output2") : null;

        // output2가 배열로 오는 경우 첫 번째 요소 사용
        if (output2 != null && output2.isArray() && !output2.isEmpty()) {
            output2 = output2.get(0);
        }
        // Null Safe 하게 값 추출
        BigDecimal totalBuyAmount = new BigDecimal(output2 != null ? output2.path("buy_excc_amt_smtl").asText("0") : "0");
        BigDecimal totalSellAmount = new BigDecimal(output2 != null ? output2.path("sll_excc_amt_smtl").asText("0") : "0");
        BigDecimal totalProfitLoss = new BigDecimal(output2 != null ? output2.path("tot_rlzt_pfls").asText("0") : "0");
        BigDecimal totalProfitLossRate = new BigDecimal(output2 != null ? output2.path("tot_pftrt").asText("0") : "0");
        BigDecimal totalFee = new BigDecimal(output2 != null ? output2.path("tot_fee").asText("0") : "0");
        BigDecimal totalTax = new BigDecimal(output2 != null ? output2.path("tot_tltx").asText("0") : "0");
        String period = String.format("%s ~ %s", request.getStartDate(), request.getEndDate());
        return TradingProfitLossResponse.builder()
                .period(period)
                .totalBuyAmount(totalBuyAmount)
                .totalSellAmount(totalSellAmount)
                .totalProfitLoss(totalProfitLoss)
                .totalProfitLossRate(totalProfitLossRate)
                .totalFee(totalFee)
                .totalTax(totalTax)
                .tradeCount(allTrades.size()) // 전체 누적 개수
                .trades(allTrades)            // 전체 누적 리스트
                .build();
    }

    /**
     * 거래일자를 YYYYMMDD 형식에서 YYYY-MM-DD 형식으로 변환
     * @param dateStr YYYYMMDD 형식의 날짜 문자열
     * @return YYYY-MM-DD 형식의 날짜 문자열, 변환 불가능한 경우 원본 반환
     */
    private String formatTradeDate(String dateStr) {
        if (dateStr == null || dateStr.length() != 8) {
            return dateStr;
        }
        try {
            return dateStr.substring(0, 4) + "-" + dateStr.substring(4, 6) + "-" + dateStr.substring(6, 8);
        } catch (Exception e) {
            log.warn("Failed to format trade date: {}", dateStr, e);
            return dateStr;
        }
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
                        .dayChangeRate(null) // 초기값은 null, 이후 조회하여 설정
                        .build();

                    holdings.add(holdingDto);
                }
            }
        }
        
        // 각 종목의 전일 대비 변동률 조회 (병렬 처리)
        fetchDayChangeRates(holdings);

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

    /**
     * 각 종목의 전일 대비 변동률을 병렬로 조회하여 설정합니다.
     * API 호출 제한(초당 20개)을 준수하기 위해 쓰로틀링을 적용합니다.
     */
    private void fetchDayChangeRates(List<StockPortfolioResponse.StockHoldingDto> holdings) {
        if (holdings == null || holdings.isEmpty()) {
            return;
        }

        // stockCode를 키로 하는 Map 생성 (인덱스 추적용)
        Map<String, Integer> stockCodeToIndex = new HashMap<>();
        for (int i = 0; i < holdings.size(); i++) {
            stockCodeToIndex.put(holdings.get(i).getStockCode(), i);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        final long[] lastCallTime = {System.currentTimeMillis()};
        final Object lock = new Object();

        for (String stockCode : stockCodeToIndex.keySet()) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // 쓰로틀링: 초당 20개 제한 준수 (호출 간격 50ms)
                    synchronized (lock) {
                        long currentTime = System.currentTimeMillis();
                        long timeSinceLastCall = currentTime - lastCallTime[0];
                        
                        if (timeSinceLastCall < THROTTLE_DELAY_MS) {
                            try {
                                Thread.sleep(THROTTLE_DELAY_MS - timeSinceLastCall);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                log.warn("Thread interrupted during throttling for stock {}", stockCode, e);
                                return;
                            }
                        }
                        lastCallTime[0] = System.currentTimeMillis();
                    }

                    // 현재가 조회 API 호출하여 전일 대비 변동률 가져오기
                    JsonNode response = koreaInvestmentClient.callApiWithParams(
                        "/uapi/domestic-stock/v1/quotations/inquire-price",
                        "FHKST01010100",
                        createPriceParams(stockCode)
                    );

                    JsonNode output = response.get("output");
                    if (output != null && output.has("prdy_ctrt")) {
                        BigDecimal dayChangeRate = new BigDecimal(output.get("prdy_ctrt").asText());
                        // 소수점 첫째 자리까지 반올림
                        dayChangeRate = dayChangeRate.setScale(1, RoundingMode.HALF_UP);
                        
                        // holding 객체의 dayChangeRate 필드 설정
                        synchronized (holdings) {
                            Integer index = stockCodeToIndex.get(stockCode);
                            if (index != null && index < holdings.size()) {
                                StockPortfolioResponse.StockHoldingDto holding = holdings.get(index);
                                StockPortfolioResponse.StockHoldingDto updatedHolding = 
                                    StockPortfolioResponse.StockHoldingDto.builder()
                                        .stockCode(holding.getStockCode())
                                        .stockName(holding.getStockName())
                                        .quantity(holding.getQuantity())
                                        .averagePrice(holding.getAveragePrice())
                                        .currentPrice(holding.getCurrentPrice())
                                        .marketValue(holding.getMarketValue())
                                        .purchasePrice(holding.getPurchasePrice())
                                        .profitLoss(holding.getProfitLoss())
                                        .profitLossRate(holding.getProfitLossRate())
                                        .sector(holding.getSector())
                                        .dayChangeRate(dayChangeRate)
                                        .build();
                                holdings.set(index, updatedHolding);
                                log.debug("Fetched dayChangeRate for {}: {}", stockCode, dayChangeRate);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch dayChangeRate for stock {}: {}", 
                        stockCode, e.getMessage());
                }
            }, executorService);

            futures.add(future);
        }

        // 모든 비동기 작업 완료 대기 (최대 30초)
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        try {
            allFutures.orTimeout(30, TimeUnit.SECONDS).join();
        } catch (Exception e) {
            log.warn("Some dayChangeRate fetches timed out or failed: {}", e.getMessage());
        }
    }

    /**
     * 현재가 조회 API 파라미터 생성
     */
    private Map<String, String> createPriceParams(String stockCode) {
        Map<String, String> params = new HashMap<>();
        params.put("FID_COND_MRKT_DIV_CODE", "J");
        params.put("FID_INPUT_ISCD", stockCode);
        return params;
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