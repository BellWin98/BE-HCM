package com.behcm.domain.tossstock.service;

import com.behcm.domain.tossstock.dto.TossOrderableResponse;
import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 주문 화면에 필요한 값을 한 번에 모아 준다.
 *
 * <p>토스에서는 시세·상하한가·매수가능금액·매도가능수량이 전부 다른 엔드포인트다. 프론트가 네 번
 * 호출하면 그만큼 화면이 늦게 완성되고 실패 조합도 네 가지가 된다. 서버가 모아서 한 번에 준다.
 *
 * <p><b>부분 실패는 그 필드만 null 로 둔다.</b> {@code TossStockService#fetchBuyingPower} 와 같은 방식이다 —
 * 시세를 못 받았다고 주문 화면 전체를 죽이면, 가격을 직접 아는 사용자까지 주문을 못 낸다.
 * 0 으로 채우지 않는 것도 같은 이유다. "0원"과 "알 수 없음"은 다른 사실이고, 전자는 거짓말이다.
 *
 * <p>캐시하지 않는다. 주문 직전에 보는 값이라 신선함이 존재 이유다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossOrderableReader {

    private final TossInvestClient tossInvestClient;

    private static final String PRICES_PATH = "/api/v1/prices";
    private static final String PRICE_LIMITS_PATH = "/api/v1/price-limits";
    private static final String BUYING_POWER_PATH = "/api/v1/buying-power";
    private static final String SELLABLE_QUANTITY_PATH = "/api/v1/sellable-quantity";

    private static final String SIDE_SELL = "SELL";

    public TossOrderableResponse read(
            TossAccountOwner owner, Long accountSeq, TossListedStock stock, String side) {

        boolean selling = SIDE_SELL.equalsIgnoreCase(side);
        boolean korean = "KR".equals(stock.marketCountry());
        // 상·하한가는 한 응답에 둘 다 들어 있다. 필드별로 부르면 호출이 두 배가 된다.
        // 미국 종목에는 가격 제한이 없어(토스도 null 을 준다) 호출 자체를 건너뛴다.
        JsonNode priceLimits = korean ? fetchPriceLimits(owner, stock.symbol()) : null;

        return TossOrderableResponse.builder()
                .symbol(stock.symbol())
                .name(stock.name())
                .marketCountry(stock.marketCountry())
                .currency(stock.currency())
                .securityType(stock.securityType())
                .locSupported(stock.locSupported())
                .lastPrice(fetchLastPrice(owner, stock.symbol()))
                .upperLimitPrice(TossJsonSupport.nullableDecimal(priceLimits, "upperLimitPrice"))
                .lowerLimitPrice(TossJsonSupport.nullableDecimal(priceLimits, "lowerLimitPrice"))
                // 매수 화면에 매도가능수량을, 매도 화면에 매수가능금액을 띄울 이유가 없다.
                // 안 쓸 값을 받으려고 외부 호출을 늘리지 않는다.
                .cashBuyingPower(selling ? null : fetchBuyingPower(owner, accountSeq, stock.currency()))
                .sellableQuantity(selling ? fetchSellableQuantity(owner, accountSeq, stock.symbol()) : null)
                .build();
    }

    private BigDecimal fetchLastPrice(TossAccountOwner owner, String symbol) {
        try {
            JsonNode prices = tossInvestClient.get(owner, PRICES_PATH, Map.of("symbols", symbol));
            if (prices.isArray() && !prices.isEmpty()) {
                return TossJsonSupport.nullableDecimal(prices.get(0), "lastPrice");
            }
        } catch (Exception e) {
            log.warn("Failed to fetch Toss last price (owner={}, symbol={})", owner, symbol, e);
        }
        return null;
    }

    /**
     * 상·하한가. 실패하면 null 을 반환해 두 필드가 함께 비게 한다 —
     * {@code TossJsonSupport.nullableDecimal} 은 노드 자체가 null 이어도 null 을 준다.
     */
    private JsonNode fetchPriceLimits(TossAccountOwner owner, String symbol) {
        try {
            return tossInvestClient.get(owner, PRICE_LIMITS_PATH, Map.of("symbol", symbol));
        } catch (Exception e) {
            log.warn("Failed to fetch Toss price limits (owner={}, symbol={})", owner, symbol, e);
            return null;
        }
    }

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

    private BigDecimal fetchSellableQuantity(TossAccountOwner owner, Long accountSeq, String symbol) {
        try {
            JsonNode result = tossInvestClient.get(
                    owner, SELLABLE_QUANTITY_PATH, Map.of("symbol", symbol), accountSeq);
            return TossJsonSupport.nullableDecimal(result, "sellableQuantity");
        } catch (Exception e) {
            log.warn("Failed to fetch Toss sellable quantity (owner={}, symbol={})", owner, symbol, e);
            return null;
        }
    }
}
