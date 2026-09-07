package com.behcm.domain.tossstock.service;

import com.behcm.domain.tossstock.dto.TossOpenOrderResponse;
import com.behcm.domain.tossstock.dto.TossOrderRequest;
import com.behcm.domain.tossstock.dto.TossOrderResponse;
import com.behcm.domain.tossstock.dto.TossOrderableResponse;
import com.behcm.domain.tossstock.dto.TossStockSearchResponse;
import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossOrderClient;
import com.behcm.global.config.toss.TossOrderClient.PlacedOrder;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 주문 오케스트레이션: 소유자 해석 → 검증 → 토스 호출 → 캐시 무효화.
 *
 * <p>조회({@code TossStockService})와 클래스를 나눈 이유는 인가 경계가 다르기 때문이다.
 * 조회는 {@code toss_access} 를 받은 가족 전원, 주문은 ADMIN 만이다 —
 * "가족 자산을 볼 수 있다"와 "남의 계좌로 주문을 낼 수 있다"는 전혀 다른 권한이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TossOrderService {

    private final TossAccountResolver accountResolver;
    private final TossOrderValidator orderValidator;
    private final TossOrderClient tossOrderClient;
    private final TossOrderCacheEvictor cacheEvictor;
    private final TossOpenOrderReader openOrderReader;
    private final TossOrderableReader orderableReader;
    private final TossStockSearchService searchService;
    private final TossStockLookup stockLookup;

    /** 화면 한 벌에 들어갈 만큼. 더 내려 봐야 스크롤만 길어진다. */
    private static final int MAX_SEARCH_LIMIT = 50;

    public List<TossStockSearchResponse> searchStocks(String query, int limit) {
        int capped = Math.min(Math.max(limit, 1), MAX_SEARCH_LIMIT);
        return searchService.search(query, capped).stream()
                .map(stock -> TossStockSearchResponse.builder()
                        .symbol(stock.symbol())
                        .name(stock.name())
                        .market(stock.market())
                        .marketCountry(stock.marketCountry())
                        .currency(stock.currency())
                        .securityType(stock.securityType())
                        .locSupported(stock.locSupported())
                        .build())
                .toList();
    }

    public TossOrderableResponse getOrderable(TossAccountOwner owner, String symbol, String side) {
        Long accountSeq = accountResolver.resolveAccountSeq(owner);
        TossListedStock stock = stockLookup.resolve(owner, symbol);
        return orderableReader.read(owner, accountSeq, stock, side);
    }

    public List<TossOpenOrderResponse> getOpenOrders(TossAccountOwner owner) {
        Long accountSeq = accountResolver.resolveAccountSeq(owner);
        return openOrderReader.read(owner, accountSeq);
    }

    /**
     * 주문을 낸다.
     *
     * <p>캐시 무효화는 <b>성공한 뒤에만</b> 한다. 실패한 주문 때문에 잔고 조회를 다시 시키는 것은
     * 외부 호출만 늘리는 일이다. 반대로 성공했는데 비우지 않으면, 방금 거래한 사람이
     * 30초 묵은 잔고를 보게 된다.
     */
    public TossOrderResponse placeOrder(TossOrderRequest request) {
        TossAccountOwner owner = TossAccountOwner.from(request.getOwner());
        Long accountSeq = accountResolver.resolveAccountSeq(owner);

        TossOrderValidator.ValidatedOrder validated = orderValidator.validate(owner, request);
        PlacedOrder placed = tossOrderClient.place(owner, accountSeq, validated.command());

        cacheEvictor.evictAccountCaches(owner);
        return new TossOrderResponse(placed.orderId(), placed.clientOrderId());
    }

    public String cancelOrder(TossAccountOwner owner, String orderId) {
        // orderId 는 URI 경로에 들어간다. 토스가 주는 값은 base64url 계열이므로 그 문자 집합만 받는다 —
        // 이상한 값이 그대로 경로에 박히면 URI 템플릿이 깨져 500 이 난다.
        if (orderId == null || !orderId.matches("^[A-Za-z0-9_-]{1,128}$")) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        Long accountSeq = accountResolver.resolveAccountSeq(owner);
        String canceled = tossOrderClient.cancel(owner, accountSeq, orderId);

        cacheEvictor.evictAccountCaches(owner);
        return canceled;
    }
}
