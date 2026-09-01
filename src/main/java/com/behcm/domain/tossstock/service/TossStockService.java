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

/**
 * 토스증권 자산·손익 조회.
 *
 * <p>한국투자증권 연동({@code domain.stock})과 의도적으로 분리되어 있다. 두 증권사는 인증 방식,
 * 계좌 지정 방식, 응답 구조, 손익률 단위가 모두 달라 한 서비스에서 다루면 분기만 늘어난다.
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
    private final TossRealizedProfitCalculator realizedProfitCalculator;

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
        JsonNode holdings = holdingsReader.read(owner, accountSeq);

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
        JsonNode profitLoss = holdings.path("profitLoss");
        JsonNode profitLossAmount = profitLoss.path("amount");
        JsonNode dailyProfitLoss = holdings.path("dailyProfitLoss");
        JsonNode dailyProfitLossAmount = dailyProfitLoss.path("amount");

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
                .cashBuyingPowerKrw(fetchBuyingPower(owner, accountSeq, "KRW"))
                .cashBuyingPowerUsd(hasUsdHolding ? fetchBuyingPower(owner, accountSeq, "USD") : null)
                .holdings(holdingDtos)
                .lastUpdated(LocalDateTime.now().toString())
                .build();
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

        Long accountSeq = accountResolver.resolveAccountSeq(owner);
        OrderHistory history = orderHistoryReader.readAll(owner, accountSeq);

        // 계좌 전체 기간을 재생해 원가를 만든 뒤, 조회 기간에 해당하는 체결만 화면에 보낸다.
        List<RealizedFill> allRealized = realizedProfitCalculator.calculate(
                history.fills(), seedAveragePrices(owner, accountSeq));

        List<TossTradeDto> trades = new ArrayList<>();
        Map<String, TotalsAccumulator> totalsByCurrency = new LinkedHashMap<>();
        boolean estimated = false;

        for (RealizedFill realized : allRealized) {
            Fill fill = realized.fill();
            LocalDate tradeDate = fill.tradeDate();
            if (tradeDate.isBefore(startDate) || tradeDate.isAfter(endDate)) {
                continue;
            }

            trades.add(toTradeDto(realized, history.names()));
            totalsByCurrency
                    .computeIfAbsent(fill.currency(), key -> new TotalsAccumulator())
                    .add(realized);
            estimated |= realized.estimated();
        }

        // 최신순 정렬 — 한투 화면과 동일한 순서를 유지한다.
        trades.sort((left, right) -> right.getTradeDate().compareTo(left.getTradeDate()));

        List<CurrencyTotals> totals = totalsByCurrency.entrySet().stream()
                .map(entry -> entry.getValue().toDto(entry.getKey()))
                .toList();

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
     * 주문 이력보다 앞서 매수한 종목의 원가를 메우기 위한 현재 보유 평균단가.
     * 시딩에 실패해도 손익 계산 자체는 진행되므로(해당 건이 추정치로 표시될 뿐) 예외를 삼킨다.
     */
    private Map<String, BigDecimal> seedAveragePrices(TossAccountOwner owner, Long accountSeq) {
        Map<String, BigDecimal> seeds = new HashMap<>();
        try {
            JsonNode items = holdingsReader.read(owner, accountSeq).path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    String symbol = item.path("symbol").asString("");
                    BigDecimal averagePrice = TossJsonSupport.nullableDecimal(item, "averagePurchasePrice");
                    if (!symbol.isBlank() && averagePrice != null) {
                        seeds.put(symbol, averagePrice);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to seed average prices for owner={}; sells without buy history will be rough", owner, e);
        }
        return seeds;
    }

    private TossTradeDto toTradeDto(RealizedFill realized, Map<String, String> names) {
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
                .tradeType(fill.side().name())
                .currency(fill.currency())
                .quantity(quantity)
                .price(price)
                .amount(amount)
                .profitLoss(realized.profitLoss())
                .profitLossRate(realized.profitLossRate())
                .fee(fill.commission())
                .tax(fill.tax())
                .estimated(realized.estimated())
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
