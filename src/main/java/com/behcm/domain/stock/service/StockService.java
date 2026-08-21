package com.behcm.domain.stock.service;

import com.behcm.domain.stock.dto.*;
import com.behcm.domain.stock.dto.TradingProfitLossResponse.TradingProfitLossDto;
import com.behcm.global.config.stock.KoreaInvestmentClient;
import com.behcm.global.config.stock.KoreaInvestmentProperties;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
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

    // 연속조회 키가 끝나지 않는 경우에도 호출이 유한하도록 상한을 둔다.
    static final int MAX_PROFIT_LOSS_PAGES = 100;
    private static final DateTimeFormatter REQUEST_DATE_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    @PreDestroy
    public void shutdownExecutor() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }

    /**
     * 계좌가 하나뿐이므로 단일 엔트리 캐시로 충분하다. 화면을 새로고침할 때마다
     * (1 + 보유종목수) 회의 외부 API 호출이 나가는 것을 짧은 TTL 로 억제한다.
     */
    @Cacheable("stockPortfolio")
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

    public TradingProfitLossResponse getTradingProfitLoss(TradingProfitLossRequest request) {
        log.debug("Trading profit loss request - startDate: {}, endDate: {}, periodType: {}",
                request.getStartDate(), request.getEndDate(), request.getPeriodType());

        // 0. 요청 검증 — null/형식오류가 아래 replace()에서 NPE 로 터져 500 이 되는 것을 막는다.
        LocalDate startDate = parseRequestDate(request.getStartDate());
        LocalDate endDate = parseRequestDate(request.getEndDate());
        if (startDate.isAfter(endDate)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

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
        // 3. 반복 조회 루프 (Pagination) — 연속키가 끝나지 않아도 MAX_PROFIT_LOSS_PAGES 에서 멈춘다.
        for (int page = 0; page < MAX_PROFIT_LOSS_PAGES; page++) {
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
                    String stockCode = trade.path("pdno").asString();
                    String stockName = trade.path("prdt_name").asString();
                    String tradeDate = formatTradeDate(trade.path("trad_dt").asString());
                    
                    // 매수 내역 처리 (buy_qty가 0이 아니면 매수 DTO 생성)
                    BigDecimal buyQty = decimal(trade, "buy_qty");
                    if (buyQty.signum() != 0) {
                        TradingProfitLossDto buyDto = TradingProfitLossDto.builder()
                                .stockCode(stockCode)
                                .stockName(stockName)
                                .tradeDate(tradeDate)
                                .tradeType("BUY")
                                .quantity(buyQty.intValue())
                                // pchs_unpr 은 보유 포지션의 평균 매입단가라 그날 체결가와 다르다.
                                // 매수금액/매수수량이 해당 행의 실제 체결 평균가다.
                                .price(unitPrice(decimal(trade, "buy_amt"), buyQty, decimal(trade, "pchs_unpr")))
                                .amount(decimal(trade, "buy_amt"))
                                .profitLoss(BigDecimal.ZERO) // 매수 시 손익 없음
                                .profitLossRate(BigDecimal.ZERO) // 매수 시 손익률 없음
                                .fee(decimal(trade, "fee"))
                                .tax(BigDecimal.ZERO) // 매수 시 세금 없음
                                .build();
                        allTrades.add(buyDto);
                    }

                    // 매도 내역 처리 (sll_qty가 0이 아니면 매도 DTO 생성)
                    BigDecimal sllQty = decimal(trade, "sll_qty");
                    if (sllQty.signum() != 0) {
                        TradingProfitLossDto sellDto = TradingProfitLossDto.builder()
                                .stockCode(stockCode)
                                .stockName(stockName)
                                .tradeDate(tradeDate)
                                .tradeType("SELL")
                                .quantity(sllQty.intValue())
                                .price(unitPrice(decimal(trade, "sll_amt"), sllQty, decimal(trade, "sll_pric")))
                                .amount(decimal(trade, "sll_amt"))
                                .profitLoss(decimal(trade, "rlzt_pfls"))
                                .profitLossRate(decimal(trade, "pfls_rt"))
                                .fee(decimal(trade, "fee"))
                                .tax(decimal(trade, "tl_tax"))
                                .build();
                        allTrades.add(sellDto);
                    }
                }
            }
            // 5. 연속 데이터 확인 (Response Body의 ctx 값 확인)
            String ctxAreaNk = response.path("ctx_area_nk100").asString().trim();
            String ctxAreaFk = response.path("ctx_area_fk100").asString().trim();
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

            if (page == MAX_PROFIT_LOSS_PAGES - 1) {
                log.warn("Trading profit loss pagination hit the {}-page cap for {} ~ {}; results may be truncated",
                        MAX_PROFIT_LOSS_PAGES, request.getStartDate(), request.getEndDate());
                break;
            }

            // API 호출 제한 고려하여 짧은 대기 시간 추가
            try {
                Thread.sleep(THROTTLE_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // 7. 거래 내역 날짜순 정렬 (최신순)
        allTrades.sort((a, b) -> b.getTradeDate().compareTo(a.getTradeDate()));
        
        // 8. 최종 결과 생성 (output2 합계 데이터 및 전체 리스트 반환)
        return parseTradingProfitLossResponse(lastResponse, request, allTrades);
    }

    private TradingProfitLossResponse parseTradingProfitLossResponse(JsonNode response, TradingProfitLossRequest request, List<TradingProfitLossDto> allTrades) {
        JsonNode output2 = firstElement(response != null ? response.get("output2") : null);

        BigDecimal totalBuyAmount = decimal(output2, "buy_excc_amt_smtl");
        BigDecimal totalSellAmount = decimal(output2, "sll_excc_amt_smtl");
        BigDecimal totalProfitLoss = decimal(output2, "tot_rlzt_pfls");
        BigDecimal totalProfitLossRate = decimal(output2, "tot_pftrt");
        BigDecimal totalFee = decimal(output2, "tot_fee");
        BigDecimal totalTax = decimal(output2, "tot_tltx");
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
     * 요청 날짜 문자열(yyyy-MM-dd)을 검증하며 파싱한다.
     * null·빈 문자열·형식 오류는 모두 INVALID_INPUT 으로 변환해 500 대신 400 이 나가게 한다.
     */
    private LocalDate parseRequestDate(String value) {
        if (value == null || value.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        try {
            return LocalDate.parse(value.trim(), REQUEST_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    /**
     * 거래 행의 1주당 체결 평균가를 구한다.
     * 한국투자증권이 내려주는 단가 필드(pchs_unpr 등)는 그날 체결가가 아니라 포지션 평균단가라
     * 거래 내역에 그대로 쓰면 안 된다. 금액/수량이 해당 행의 실제 체결 평균가다.
     * 금액이나 수량이 없어 계산할 수 없을 때만 API 가 준 단가로 대체한다.
     */
    private BigDecimal unitPrice(BigDecimal amount, BigDecimal quantity, BigDecimal fallback) {
        if (quantity.signum() == 0 || amount.signum() == 0) {
            return fallback;
        }
        return amount.divide(quantity, 2, RoundingMode.HALF_UP);
    }

    /**
     * 배열이면 첫 요소를, 객체면 그대로 반환한다. 비었거나 null 이면 null 을 반환한다.
     * 한국투자증권 응답의 output2 는 TR 에 따라 배열/객체가 섞여 오고, 빈 배열로 오는 경우도 있다.
     */
    private JsonNode firstElement(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            return node.isEmpty() ? null : node.get(0);
        }
        return node;
    }

    /**
     * 노드에서 숫자 필드를 안전하게 읽는다. 필드가 없거나 빈 문자열/비숫자면 0 을 반환한다.
     * 한국투자증권 API 는 값이 없는 수치 필드를 빈 문자열로 내려주는 경우가 있다.
     */
    private BigDecimal decimal(JsonNode node, String fieldName) {
        if (node == null) {
            return BigDecimal.ZERO;
        }
        String raw = node.path(fieldName).asString("").trim();
        if (raw.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            log.warn("Unparsable numeric field {}='{}', defaulting to 0", fieldName, raw);
            return BigDecimal.ZERO;
        }
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

        // output2 는 배열로 올 때도 객체로 올 때도 있고, 빈 배열로 오기도 한다.
        JsonNode summary = firstElement(response.get("output2"));
        List<StockPortfolioResponse.StockHoldingDto> holdings = new ArrayList<>();

        if (output1.isArray()) {
            for (JsonNode holding : output1) {
                if (decimal(holding, "hldg_qty").compareTo(BigDecimal.ZERO) > 0) {
                    StockPortfolioResponse.StockHoldingDto holdingDto = StockPortfolioResponse.StockHoldingDto.builder()
                        .stockCode(holding.path("pdno").asString(""))
                        .stockName(holding.path("prdt_name").asString(""))
                        .quantity(decimal(holding, "hldg_qty").intValue())
                        .averagePrice(decimal(holding, "pchs_avg_pric"))
                        .currentPrice(decimal(holding, "prpr"))
                        .marketValue(decimal(holding, "evlu_amt"))
                        .purchasePrice(decimal(holding, "pchs_amt"))
                        .profitLoss(decimal(holding, "evlu_pfls_amt"))
                        .profitLossRate(decimal(holding, "evlu_pfls_rt"))
                        .sector("")
                        .dayChangeRate(null) // 초기값은 null, 이후 조회하여 설정
                        .build();

                    holdings.add(holdingDto);
                }
            }
        }

        // 각 종목의 전일 대비 변동률 조회 (병렬 처리)
        fetchDayChangeRates(holdings);

        BigDecimal totalMarketValue = decimal(summary, "evlu_amt_smtl_amt");
        BigDecimal totalBuyValue = decimal(summary, "pchs_amt_smtl_amt");
        BigDecimal totalProfitLoss = decimal(summary, "evlu_pfls_smtl_amt");
        // 매입금액이 0이면(무상증자·대용주 등) 나눌 수 없으므로 수익률은 0으로 둔다.
        BigDecimal totalProfitLossRate = totalBuyValue.signum() == 0
                ? BigDecimal.ZERO
                : totalProfitLoss.divide(totalBuyValue, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
        BigDecimal depositToday = decimal(summary, "dnca_tot_amt");
        BigDecimal depositD2 = decimal(summary, "prvs_rcdl_excc_amt");

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
            .stockName(output.get("hts_kor_isnm").asString())
            .currentPrice(new BigDecimal(output.get("stck_prpr").asString()))
            .changeAmount(new BigDecimal(output.get("prdy_vrss").asString()))
            .changeRate(new BigDecimal(output.get("prdy_ctrt").asString()))
            .changeDirection(output.get("prdy_vrss_sign").asString())
            .volume(new BigDecimal(output.get("acml_vol").asString()))
            .highPrice(new BigDecimal(output.get("stck_hgpr").asString()))
            .lowPrice(new BigDecimal(output.get("stck_lwpr").asString()))
            .openPrice(new BigDecimal(output.get("stck_oprc").asString()))
            .marketType(output.get("mrkt_ctg").asString())
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

        // 호출 시작 시각을 THROTTLE_DELAY_MS 간격으로 어긋나게 배치해 초당 호출 수를 제한한다.
        // 락을 잡은 채 sleep 하면 스레드풀이 직렬화되어 병렬 처리가 무의미해지므로, 각 작업이
        // 자기 차례까지만 기다리도록 한다. 같은 종목을 두 번 보유한 경우도 있으므로 인덱스로 순회한다.
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        final long startTime = System.currentTimeMillis();

        for (int i = 0; i < holdings.size(); i++) {
            final StockPortfolioResponse.StockHoldingDto holding = holdings.get(i);
            final String stockCode = holding.getStockCode();
            final long scheduledOffsetMs = i * THROTTLE_DELAY_MS;

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    long waitMs = scheduledOffsetMs - (System.currentTimeMillis() - startTime);
                    if (waitMs > 0) {
                        Thread.sleep(waitMs);
                    }

                    // 현재가 조회 API 호출하여 전일 대비 변동률 가져오기
                    JsonNode response = koreaInvestmentClient.callApiWithParams(
                        "/uapi/domestic-stock/v1/quotations/inquire-price",
                        "FHKST01010100",
                        createPriceParams(stockCode)
                    );

                    JsonNode output = response.get("output");
                    if (output != null && output.has("prdy_ctrt")) {
                        // 소수점 첫째 자리까지 반올림
                        BigDecimal dayChangeRate = decimal(output, "prdy_ctrt")
                                .setScale(1, RoundingMode.HALF_UP);
                        holding.setDayChangeRate(dayChangeRate);
                        log.debug("Fetched dayChangeRate for {}: {}", stockCode, dayChangeRate);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while throttling dayChangeRate fetch for stock {}", stockCode);
                } catch (Exception e) {
                    log.warn("Failed to fetch dayChangeRate for stock {}: {}", stockCode, e.getMessage());
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
            .stockName(stockData.get("hts_kor_isnm") != null ? stockData.get("hts_kor_isnm").asString() : "")
            .marketType(stockData.get("mrkt_ctg") != null ? stockData.get("mrkt_ctg").asString() : "")
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