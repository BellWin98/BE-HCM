package com.behcm.domain.tossstock.service;

import com.behcm.domain.tossstock.dto.TossOrderRequest;
import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossOrderClient.TossOrderCommand;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 주문을 토스로 보내기 전 마지막 관문.
 *
 * <p><b>무엇을 여기서 막고 무엇을 토스에 맡기는가</b>는 한 가지 기준으로 갈랐다 —
 * 우리가 확인한 순간과 주문이 원장에 닿는 순간 사이에 <b>변하는 값인가</b>.
 *
 * <ul>
 *   <li>여기서 막는 것: 종목의 시장, 주문 유형과 가격의 조합, 수량의 부호·자릿수.
 *       전부 그 사이에 변하지 않으므로, 왕복 한 번을 버려 가며 토스에게 물어볼 이유가 없다.</li>
 *   <li>토스에 맡기는 것: 매수가능금액, 매도가능수량, 상·하한가, 호가 단위, 장 운영시간, 거래정지.
 *       전부 변한다. 우리가 미리 막으면 <b>실제로는 가능한 주문</b>을 우리 손으로 거부하게 된다.</li>
 * </ul>
 *
 * <p>특히 <b>호가 단위 표는 들지 않는다</b>. KRX 호가 단위는 가격 구간·시장·ETF 여부로 갈리고
 * 제도 개정으로 바뀌는데, 우리가 복제한 표가 낡는 순간 정상 주문이 막힌다.
 * 화면(프론트)이 스테퍼를 위해 이미 표를 들고 있어 입력 단계에서 대부분 맞춰지고,
 * 남는 어긋남은 토스가 400 으로 알려 준다.
 */
@Component
@RequiredArgsConstructor
public class TossOrderValidator {

    private final TossStockLookup stockLookup;

    private static final String LIMIT = "LIMIT";
    private static final String CLS = "CLS";
    private static final String DAY = "DAY";

    /** $1 을 경계로 허용 소수 자릿수가 갈린다(토스 스펙). */
    private static final BigDecimal ONE_DOLLAR = BigDecimal.ONE;
    private static final int US_SCALE_ABOVE_ONE_DOLLAR = 2;
    private static final int US_SCALE_BELOW_ONE_DOLLAR = 4;

    /** 검증을 통과한 주문과, 그 과정에서 이미 알아낸 종목 정보(화면 응답에 다시 쓴다). */
    public record ValidatedOrder(TossOrderCommand command, TossListedStock stock) { }

    public ValidatedOrder validate(TossAccountOwner owner, TossOrderRequest request) {
        TossListedStock stock = stockLookup.resolve(owner, request.getSymbol());

        String orderType = request.getOrderType();
        String timeInForce = normalizeTimeInForce(request.getTimeInForce());
        BigDecimal quantity = parseQuantity(request.getQuantity());
        BigDecimal price = parsePrice(request.getPrice());

        validateLoc(stock, orderType, timeInForce);
        validatePricePresence(orderType, price);
        if (price != null) {
            validatePriceScale(stock, price);
        }

        return new ValidatedOrder(
                new TossOrderCommand(
                        resolveClientOrderId(request.getClientOrderId()),
                        stock.symbol(),
                        request.getSide(),
                        orderType,
                        timeInForce,
                        quantity,
                        price,
                        request.isConfirmHighValueOrder()
                ),
                stock
        );
    }

    private String normalizeTimeInForce(String timeInForce) {
        return timeInForce == null || timeInForce.isBlank() ? DAY : timeInForce;
    }

    /**
     * LOC = {@code LIMIT} + {@code CLS} 이고 토스는 이 조합을 <b>미국 주식에만</b> 허용한다.
     * 종목의 시장은 변하지 않으므로 여기서 끝낸다 — 화면도 국내 종목에는 LOC 을 그리지 않지만,
     * 화면 상태는 조작될 수 있으므로 서버가 다시 본다.
     */
    private void validateLoc(TossListedStock stock, String orderType, String timeInForce) {
        if (!CLS.equals(timeInForce)) {
            return;
        }
        if (!stock.locSupported() || !LIMIT.equals(orderType)) {
            throw new CustomException(ErrorCode.TOSS_ORDER_LOC_NOT_SUPPORTED);
        }
    }

    private void validatePricePresence(String orderType, BigDecimal price) {
        boolean limitOrder = LIMIT.equals(orderType);
        if (limitOrder && price == null) {
            throw new CustomException(ErrorCode.TOSS_ORDER_INVALID);
        }
        // 시장가에 가격을 실어 보내면 토스가 거부한다. 화면 상태가 꼬였다는 신호이기도 하다.
        if (!limitOrder && price != null) {
            throw new CustomException(ErrorCode.TOSS_ORDER_INVALID);
        }
    }

    /**
     * 자릿수만 본다(호가 단위는 보지 않는다 — 클래스 javadoc 참조).
     * 국내는 원 단위 정수, 미국은 $1 을 경계로 2자리/4자리다.
     */
    private void validatePriceScale(TossListedStock stock, BigDecimal price) {
        int scale = Math.max(0, price.stripTrailingZeros().scale());
        int allowed = "KR".equals(stock.marketCountry())
                ? 0
                : (price.compareTo(ONE_DOLLAR) >= 0 ? US_SCALE_ABOVE_ONE_DOLLAR : US_SCALE_BELOW_ONE_DOLLAR);

        if (scale > allowed) {
            throw new CustomException(ErrorCode.TOSS_ORDER_INVALID);
        }
    }

    private BigDecimal parseQuantity(String raw) {
        BigDecimal quantity = parseDecimal(raw);
        // 소수점 수량은 미국 시장가 매도에만 허용되는데 그건 이번 범위가 아니다.
        if (quantity == null || quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 0) {
            throw new CustomException(ErrorCode.TOSS_ORDER_INVALID);
        }
        return quantity;
    }

    private BigDecimal parsePrice(String raw) {
        BigDecimal price = parseDecimal(raw);
        if (price != null && price.signum() <= 0) {
            throw new CustomException(ErrorCode.TOSS_ORDER_INVALID);
        }
        return price;
    }

    private BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new CustomException(ErrorCode.TOSS_ORDER_INVALID);
        }
    }

    /**
     * 멱등키는 프론트가 만드는 게 원칙이다(재시도가 같은 키로 나가야 이중 주문이 안 난다).
     * 그래도 비어 오면 여기서 채운다 — 멱등키 없이 나가는 주문을 만들지 않기 위해서다.
     */
    private String resolveClientOrderId(String clientOrderId) {
        if (clientOrderId != null && !clientOrderId.isBlank()) {
            return clientOrderId;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
