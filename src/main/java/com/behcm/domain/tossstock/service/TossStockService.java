package com.behcm.domain.tossstock.service;

import com.behcm.domain.tossstock.dto.TossOwnerResponse;
import com.behcm.domain.tossstock.dto.TossPortfolioResponse;
import com.behcm.domain.tossstock.dto.TossPortfolioResponse.TossHoldingDto;
import com.behcm.domain.tossstock.dto.TossRealizedProfitRequest;
import com.behcm.domain.tossstock.dto.TossRealizedProfitResponse;
import com.behcm.domain.tossstock.dto.TossRealizedProfitResponse.CurrencyTotals;
import com.behcm.domain.tossstock.dto.TossRealizedProfitResponse.TossTradeDto;
import com.behcm.domain.tossstock.service.TossOrderHistoryReader.OrderHistory;
import com.behcm.domain.tossstock.service.TossRealizedProfitCalculator.Fill;
import com.behcm.domain.tossstock.service.TossRealizedProfitCalculator.RealizedFill;
import com.behcm.domain.tossstock.service.TossRealizedProfitCalculator.Replay;
import com.behcm.domain.tossstock.service.TossRealizedProfitCalculator.TradeSide;
import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestClient;
import com.behcm.global.config.toss.TossInvestProperties;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

/**
 * 토스증권 자산·손익 조회.
 *
 * <p>한국투자증권 연동({@code domain.stock})과 의도적으로 분리되어 있다. 두 증권사는 인증 방식,
 * 계좌 지정 방식, 응답 구조, 손익률 단위가 모두 달라 한 서비스에서 다루면 분기만 늘어난다.
 *
 * <p>국내(KRW)·해외(USD) 금액은 <b>통화별로 나눠 그대로 내려준다</b>. 환율로 합쳐 주지 않는 이유는
 * 평단·매입금액·손익이 매수/매도 시점 환율로 확정된 과거 금액이기 때문이다 — 오늘 환율을 곱하면
 * 환율이 움직이는 것만으로 손익이 변한 것처럼 보인다. 환율은 참고 표기용으로만 함께 내려간다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TossStockService {

    private final TossInvestClient tossInvestClient;
    private final TossInvestProperties properties;
    private final TossAccountResolver accountResolver;
    private final TossHoldingsReader holdingsReader;
    private final TossOrderHistoryReader orderHistoryReader;
    private final TossExchangeRateReader exchangeRateReader;
    private final TossRealizedProfitCalculator realizedProfitCalculator;
    private final TossApiExecutor apiExecutor;

    private static final String BUYING_POWER_PATH = "/api/v1/buying-power";
    private static final DateTimeFormatter REQUEST_DATE_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * 연동된 계좌 소유자 목록. 프론트의 계좌 전환 세그먼트를 채운다.
     */
    public List<TossOwnerResponse> getOwners() {
        return properties.configuredOwners().stream()
                .map(owner -> new TossOwnerResponse(owner.name(), owner.getDisplayName()))
                .toList();
    }

    public TossPortfolioResponse getPortfolio(TossAccountOwner owner) {
        Long accountSeq = accountResolver.resolveAccountSeq(owner);

        // 1단계: 보유주식과 원화 매수가능금액은 서로 무관해 동시에 보낸다(Rate Limits Group 도 ASSET / ORDER_INFO 로 다르다).
        CompletableFuture<BigDecimal> cashBuyingPowerKrw =
                apiExecutor.supply(() -> fetchBuyingPower(owner, accountSeq, "KRW"));
        JsonNode holdings = TossApiExecutor.join(apiExecutor.supply(() -> holdingsReader.read(owner, accountSeq)));

        List<TossHoldingDto> holdingDtos = new ArrayList<>();
        boolean hasUsdHolding = false;

        JsonNode items = holdings.path("items");
        if (items.isArray()) {
            for (JsonNode item : items) {
                holdingDtos.add(toHoldingDto(item));
                if ("USD".equals(item.path("currency").asString(""))) {
                    hasUsdHolding = true;
                }
            }
        }

        JsonNode totalPurchase = holdings.path("totalPurchaseAmount");
        JsonNode marketValue = holdings.path("marketValue").path("amount");
        JsonNode marketValueAfterCost = holdings.path("marketValue").path("amountAfterCost");
        JsonNode profitLoss = holdings.path("profitLoss");
        JsonNode profitLossAmount = profitLoss.path("amount");
        JsonNode profitLossAmountAfterCost = profitLoss.path("amountAfterCost");
        JsonNode dailyProfitLoss = holdings.path("dailyProfitLoss");
        JsonNode dailyProfitLossAmount = dailyProfitLoss.path("amount");

        // 2단계: 환율과 달러 매수가능금액은 해외 종목이 있을 때만 의미가 있어 보유주식을 기다리지만, 그 둘끼리는 무관하다.
        CompletableFuture<JsonNode> exchangeRateFuture = hasUsdHolding
                ? apiExecutor.supply(() -> fetchUsdKrwRate(owner))
                : CompletableFuture.completedFuture(null);
        CompletableFuture<BigDecimal> cashBuyingPowerUsd = hasUsdHolding
                ? apiExecutor.supply(() -> fetchBuyingPower(owner, accountSeq, "USD"))
                : CompletableFuture.completedFuture(null);
        JsonNode exchangeRate = TossApiExecutor.join(exchangeRateFuture);

        return TossPortfolioResponse.builder()
                .owner(owner.name())
                .ownerName(owner.getDisplayName())
                .totalPurchaseAmountKrw(TossJsonSupport.decimal(totalPurchase, "krw"))
                .totalPurchaseAmountUsd(TossJsonSupport.nullableDecimal(totalPurchase, "usd"))
                .totalMarketValueKrw(TossJsonSupport.decimal(marketValue, "krw"))
                .totalMarketValueUsd(TossJsonSupport.nullableDecimal(marketValue, "usd"))
                .totalProfitLossKrw(TossJsonSupport.decimal(profitLossAmount, "krw"))
                .totalProfitLossUsd(TossJsonSupport.nullableDecimal(profitLossAmount, "usd"))
                .totalProfitLossRate(TossJsonSupport.percent(profitLoss, "rate"))
                .dailyProfitLossKrw(TossJsonSupport.decimal(dailyProfitLossAmount, "krw"))
                .dailyProfitLossUsd(TossJsonSupport.nullableDecimal(dailyProfitLossAmount, "usd"))
                .dailyProfitLossRate(TossJsonSupport.percent(dailyProfitLoss, "rate"))
                .totalMarketValueAfterCostKrw(TossJsonSupport.decimal(marketValueAfterCost, "krw"))
                .totalMarketValueAfterCostUsd(TossJsonSupport.nullableDecimal(marketValueAfterCost, "usd"))
                .totalProfitLossAfterCostKrw(TossJsonSupport.decimal(profitLossAmountAfterCost, "krw"))
                .totalProfitLossAfterCostUsd(TossJsonSupport.nullableDecimal(profitLossAmountAfterCost, "usd"))
                .totalProfitLossRateAfterCost(TossJsonSupport.percent(profitLoss, "rateAfterCost"))
                .usdKrwRate(TossJsonSupport.nullableDecimal(exchangeRate, "rate"))
                .usdKrwMidRate(TossJsonSupport.nullableDecimal(exchangeRate, "midRate"))
                .usdKrwRateChangeType(TossJsonSupport.text(exchangeRate, "rateChangeType"))
                .usdKrwRateAsOf(TossJsonSupport.text(exchangeRate, "validFrom"))
                .cashBuyingPowerKrw(TossApiExecutor.join(cashBuyingPowerKrw))
                .cashBuyingPowerUsd(TossApiExecutor.join(cashBuyingPowerUsd))
                .holdings(holdingDtos)
                .lastUpdated(LocalDateTime.now().toString())
                .build();
    }

    /**
     * USD→KRW 환율. 화면에 참고 표기로만 쓰이고 금액 환산에는 쓰지 않는다.
     * 이 값이 없다고 자산 화면 전체를 죽이지 않는다 — 통화별 합계는 그대로 유효하므로,
     * 실패 시 null 을 반환해 환율 표기만 생략되게 한다.
     */
    private JsonNode fetchUsdKrwRate(TossAccountOwner owner) {
        try {
            return exchangeRateReader.readUsdToKrw(owner);
        } catch (Exception e) {
            log.warn("Failed to fetch Toss USD/KRW exchange rate (owner={})", owner, e);
            return null;
        }
    }

    private TossHoldingDto toHoldingDto(JsonNode item) {
        JsonNode marketValue = item.path("marketValue");
        JsonNode profitLoss = item.path("profitLoss");
        JsonNode dailyProfitLoss = item.path("dailyProfitLoss");
        JsonNode cost = item.path("cost");

        return TossHoldingDto.builder()
                .symbol(item.path("symbol").asString(""))
                .name(item.path("name").asString(""))
                .marketCountry(item.path("marketCountry").asString(""))
                .currency(item.path("currency").asString(""))
                .quantity(TossJsonSupport.decimal(item, "quantity"))
                .lastPrice(TossJsonSupport.decimal(item, "lastPrice"))
                .averagePurchasePrice(TossJsonSupport.decimal(item, "averagePurchasePrice"))
                .purchaseAmount(TossJsonSupport.decimal(marketValue, "purchaseAmount"))
                .marketValue(TossJsonSupport.decimal(marketValue, "amount"))
                .marketValueAfterCost(TossJsonSupport.decimal(marketValue, "amountAfterCost"))
                .profitLoss(TossJsonSupport.decimal(profitLoss, "amount"))
                .profitLossAfterCost(TossJsonSupport.decimal(profitLoss, "amountAfterCost"))
                .profitLossRate(TossJsonSupport.percent(profitLoss, "rate"))
                .profitLossRateAfterCost(TossJsonSupport.percent(profitLoss, "rateAfterCost"))
                .dailyProfitLoss(TossJsonSupport.decimal(dailyProfitLoss, "amount"))
                .dailyProfitLossRate(TossJsonSupport.percent(dailyProfitLoss, "rate"))
                .commission(TossJsonSupport.decimal(cost, "commission"))
                .tax(TossJsonSupport.decimal(cost, "tax"))
                .build();
    }

    /**
     * 현금 매수가능금액. 보유 현황이 주된 정보이므로, 이 값만 실패했다고 화면 전체를 죽이지 않는다.
     * 실패 시 null 을 반환해 화면에 "-" 로 표시되게 한다(0 으로 채우면 잔고가 없는 것으로 오해한다).
     */
    private BigDecimal fetchBuyingPower(TossAccountOwner owner, Long accountSeq, String currency) {
        try {
            JsonNode result = tossInvestClient.get(
                    owner, BUYING_POWER_PATH, Map.of("currency", currency), accountSeq);
            return TossJsonSupport.nullableDecimal(result, "cashBuyingPower");
        } catch (Exception e) {
            log.warn("Failed to fetch Toss buying power (owner={}, currency={})", owner, currency, e);
            return null;
        }
    }

    public TossRealizedProfitResponse getRealizedProfit(TossRealizedProfitRequest request) {
        TossAccountOwner owner = TossAccountOwner.from(request.getOwner());
        LocalDate startDate = parseRequestDate(request.getStartDate());
        LocalDate endDate = parseRequestDate(request.getEndDate());
        if (startDate.isAfter(endDate)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        long startedAt = System.nanoTime();
        Long accountSeq = accountResolver.resolveAccountSeq(owner);

        long historyStartedAt = System.nanoTime();
        OrderHistory history = orderHistoryReader.readAll(owner, accountSeq);
        long historyNanos = System.nanoTime() - historyStartedAt;

        long seedStartedAt = System.nanoTime();
        HoldingsSnapshot holdings = readHoldingsSnapshot(owner, accountSeq);
        long seedNanos = System.nanoTime() - seedStartedAt;

        // 계좌 전체 기간을 재생해 원가를 만든 뒤, 조회 기간에 해당하는 체결만 화면에 보낸다.
        long calcStartedAt = System.nanoTime();
        Replay replay = realizedProfitCalculator.replay(history.fills(), holdings.averagePrices());
        List<RealizedFill> allRealized = replay.fills();
        Set<String> mismatchedSymbols = findQuantityMismatches(owner, replay, holdings);
        long calcNanos = System.nanoTime() - calcStartedAt;

        List<TossTradeDto> trades = new ArrayList<>();
        Map<String, TotalsAccumulator> totalsByCurrency = new LinkedHashMap<>();
        boolean estimated = false;

        for (RealizedFill realized : allRealized) {
            Fill fill = realized.fill();
            LocalDate tradeDate = fill.tradeDate();
            if (tradeDate.isBefore(startDate) || tradeDate.isAfter(endDate)) {
                continue;
            }

            // 잔고가 어긋난 종목의 매도는 원가가 틀렸을 수 있다. 매수는 체결 사실 그대로라 건드리지 않는다.
            boolean tradeEstimated = realized.estimated()
                    || (fill.side() == TradeSide.SELL && mismatchedSymbols.contains(fill.symbol()));
            trades.add(toTradeDto(realized, history.names(), tradeEstimated));
            totalsByCurrency
                    .computeIfAbsent(fill.currency(), key -> new TotalsAccumulator())
                    .add(realized);
            estimated |= tradeEstimated;
        }

        // 최신순 정렬 — 한투 화면과 동일한 순서를 유지한다.
        // 같은 날 여러 번 체결된 건은 시각까지 비교해야 최신 체결이 위로 온다.
        trades.sort((left, right) -> right.getTradeDateTime().compareTo(left.getTradeDateTime()));

        List<CurrencyTotals> totals = totalsByCurrency.entrySet().stream()
                .map(entry -> entry.getValue().toDto(entry.getKey()))
                .toList();

        // 구간별 소요 시간. history 안에서 전체 재적재였는지 증분이었는지는
        // TossOrderHistoryReader 가 남기는 mode=full|incremental 줄이 알려준다.
        log.info("Toss realized profit: owner={}, period={}~{}, fills={}, trades={}, "
                        + "history={}ms, seed={}ms, calc={}ms, total={}ms",
                owner, request.getStartDate(), request.getEndDate(),
                history.fills().size(), trades.size(),
                historyNanos / 1_000_000L, seedNanos / 1_000_000L, calcNanos / 1_000_000L,
                (System.nanoTime() - startedAt) / 1_000_000L);

        return TossRealizedProfitResponse.builder()
                .owner(owner.name())
                .ownerName(owner.getDisplayName())
                .period(String.format("%s ~ %s", request.getStartDate(), request.getEndDate()))
                .totals(totals)
                .tradeCount(trades.size())
                .trades(trades)
                .estimated(estimated)
                .build();
    }

    /**
     * 현재 보유 현황 중 실현손익 계산에 쓰는 두 값.
     *
     * @param averagePrices 주문 이력보다 앞서 매수한 종목의 원가를 메우는 평균단가(부족분 시딩)
     * @param quantities    재생 잔고와 대조할 보유 수량. 조회에 실패했으면 null — 대조를 건너뛴다.
     */
    private record HoldingsSnapshot(Map<String, BigDecimal> averagePrices, Map<String, BigDecimal> quantities) { }

    /**
     * 보유주식을 한 번 읽어 시딩과 대조에 함께 쓴다.
     * 실패해도 손익 계산 자체는 진행되므로(해당 건이 추정치로 표시될 뿐) 예외를 삼킨다.
     */
    private HoldingsSnapshot readHoldingsSnapshot(TossAccountOwner owner, Long accountSeq) {
        Map<String, BigDecimal> averagePrices = new HashMap<>();
        Map<String, BigDecimal> quantities = new HashMap<>();
        try {
            JsonNode items = holdingsReader.read(owner, accountSeq).path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    String symbol = item.path("symbol").asString("");
                    if (symbol.isBlank()) {
                        continue;
                    }
                    BigDecimal averagePrice = TossJsonSupport.nullableDecimal(item, "averagePurchasePrice");
                    if (averagePrice != null) {
                        averagePrices.put(symbol, averagePrice);
                    }
                    BigDecimal quantity = TossJsonSupport.nullableDecimal(item, "quantity");
                    if (quantity != null) {
                        quantities.put(symbol, quantity);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read Toss holdings for owner={}; sells without buy history will be rough "
                    + "and the replay is not reconciled", owner, e);
            return new HoldingsSnapshot(averagePrices, null);
        }
        return new HoldingsSnapshot(averagePrices, quantities);
    }

    /**
     * 재생이 끝난 잔여 수량이 실제 보유 수량과 다른 종목.
     *
     * <p>토스 주문내역은 Open API 가 지원하는 호가 유형의 주문만 돌려준다 — 앱에서 낸 시간외 종가 주문,
     * 액면분할·무상증자, 타사 대체입고는 빠진다. 그런 거래가 있으면 이동평균 원가가 조용히 틀리는데,
     * 수량이 모자라지 않는 한 부족분 메우기로는 드러나지 않는다. 잔고 대조가 그 신호다.
     * 보유에 없는 종목은 0주로 본다(전량 매도했다면 재생 잔고도 0 이어야 한다).
     */
    private Set<String> findQuantityMismatches(TossAccountOwner owner, Replay replay, HoldingsSnapshot holdings) {
        if (holdings.quantities() == null) {
            return Set.of();
        }
        Set<String> mismatched = new TreeSet<>();
        replay.remainingQuantities().forEach((symbol, replayed) -> {
            BigDecimal held = holdings.quantities().getOrDefault(symbol, BigDecimal.ZERO);
            if (replayed.compareTo(held) != 0) {
                mismatched.add(symbol);
            }
        });
        if (!mismatched.isEmpty()) {
            log.info("Toss realized profit replay disagrees with holdings (owner={}, symbols={}); "
                    + "marking their sells as estimated", owner, mismatched);
        }
        return mismatched;
    }

    private TossTradeDto toTradeDto(RealizedFill realized, Map<String, String> names, boolean estimated) {
        Fill fill = realized.fill();
        BigDecimal quantity = fill.quantity();
        BigDecimal amount = fill.amount();

        // 1주당 실제 체결 평균가. 수량이 0이면 나눌 수 없다.
        BigDecimal price = quantity.signum() == 0
                ? BigDecimal.ZERO
                : amount.divide(quantity, MONEY_SCALE, RoundingMode.HALF_UP);

        return TossTradeDto.builder()
                .symbol(fill.symbol())
                .name(names.getOrDefault(fill.symbol(), fill.symbol()))
                .tradeDate(fill.tradeDate().toString())
                .tradeDateTime(fill.executedAt().toString())
                .tradeType(fill.side().name())
                .currency(fill.currency())
                .quantity(quantity)
                .price(price)
                .amount(amount)
                .profitLoss(realized.profitLoss())
                .profitLossRate(realized.profitLossRate())
                .fee(fill.commission())
                .tax(fill.tax())
                .estimated(estimated)
                .build();
    }

    /**
     * 통화별 합계 누적기. 실현손익률은 <b>매도된 물량의 원가</b> 대비로 계산한다
     * (매수금액 전체로 나누면 아직 팔지 않은 물량까지 분모에 들어가 손익률이 희석된다).
     */
    private static final class TotalsAccumulator {

        private BigDecimal buyAmount = BigDecimal.ZERO;
        private BigDecimal sellAmount = BigDecimal.ZERO;
        private BigDecimal profitLoss = BigDecimal.ZERO;
        private BigDecimal fee = BigDecimal.ZERO;
        private BigDecimal tax = BigDecimal.ZERO;
        private BigDecimal soldCostBasis = BigDecimal.ZERO;
        private int tradeCount = 0;

        private void add(RealizedFill realized) {
            Fill fill = realized.fill();
            tradeCount++;
            fee = fee.add(fill.commission());
            tax = tax.add(fill.tax());

            if (fill.side() == TradeSide.SELL) {
                sellAmount = sellAmount.add(fill.amount());
                profitLoss = profitLoss.add(realized.profitLoss());
                // 원가 = 실수령액 − 실현손익
                BigDecimal proceeds = fill.amount().subtract(fill.commission()).subtract(fill.tax());
                soldCostBasis = soldCostBasis.add(proceeds.subtract(realized.profitLoss()));
            } else {
                buyAmount = buyAmount.add(fill.amount());
            }
        }

        private CurrencyTotals toDto(String currency) {
            BigDecimal rate = soldCostBasis.signum() == 0
                    ? BigDecimal.ZERO
                    : profitLoss.divide(soldCostBasis, 12, RoundingMode.HALF_UP)
                            .multiply(HUNDRED)
                            .setScale(RATE_SCALE, RoundingMode.HALF_UP);

            return CurrencyTotals.builder()
                    .currency(currency)
                    .totalBuyAmount(buyAmount)
                    .totalSellAmount(sellAmount)
                    .totalProfitLoss(profitLoss)
                    .totalProfitLossRate(rate)
                    .totalFee(fee)
                    .totalTax(tax)
                    .tradeCount(tradeCount)
                    .build();
        }
    }

    /**
     * 요청 날짜(yyyy-MM-dd)를 검증하며 파싱한다. 형식 오류가 500 이 아니라 400 으로 나가게 한다.
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
}
