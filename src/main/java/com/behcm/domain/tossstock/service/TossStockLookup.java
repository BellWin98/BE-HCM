package com.behcm.domain.tossstock.service;

import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestClient;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * 심볼 하나를 종목 정보로 바꾼다.
 *
 * <p>유니버스를 먼저 본다 — 외부 호출이 없어 주문 경로가 그만큼 짧아진다.
 * 없으면 {@code GET /api/v1/stocks} 로 직접 조회한다. 유니버스는 하루 1회 갱신이라
 * <b>오늘 상장한 종목</b>이 빠져 있을 수 있는데, 그것 때문에 매매를 막을 이유는 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossStockLookup {

    private final TossStockUniverse universe;
    private final TossInvestClient tossInvestClient;

    private static final String STOCKS_PATH = "/api/v1/stocks";

    /**
     * @throws CustomException 종목을 찾을 수 없으면 {@link ErrorCode#TOSS_STOCK_NOT_FOUND}
     */
    public TossListedStock resolve(TossAccountOwner owner, String symbol) {
        return universe.findBySymbol(symbol)
                .orElseGet(() -> fetch(owner, symbol));
    }

    private TossListedStock fetch(TossAccountOwner owner, String symbol) {
        JsonNode stocks;
        try {
            stocks = tossInvestClient.get(owner, STOCKS_PATH, Map.of("symbols", symbol));
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to look up Toss stock symbol={}", symbol, e);
            throw new CustomException(ErrorCode.TOSS_STOCK_NOT_FOUND);
        }

        if (!stocks.isArray() || stocks.isEmpty()) {
            throw new CustomException(ErrorCode.TOSS_STOCK_NOT_FOUND);
        }

        JsonNode stock = stocks.get(0);
        String resolvedSymbol = stock.path("symbol").asString("");
        if (resolvedSymbol.isBlank()) {
            throw new CustomException(ErrorCode.TOSS_STOCK_NOT_FOUND);
        }

        // 이 응답에는 market 이 들어 있어 국가·통화·LOC 지원 여부를 유니버스와 똑같은 규칙으로 파생시킬 수 있다.
        return TossListedStock.of(
                resolvedSymbol,
                stock.path("name").asString(resolvedSymbol),
                stock.path("market").asString(""),
                stock.path("securityType").asString(""),
                stock.path("isCommonShare").asBoolean(true)
        );
    }
}
