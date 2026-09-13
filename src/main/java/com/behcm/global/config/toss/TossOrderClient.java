package com.behcm.global.config.toss;

import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 토스증권 주문 실행 클라이언트.
 *
 * <p>{@link TossInvestClient#post} 를 호출하는 <b>유일한</b> 클래스다. 쓰기 경로를 이 한 곳으로 모아
 * 두면 "어디서 주문이 나가는가"에 대한 답이 항상 하나다 — 도메인 서비스는 POST 를 부를 방법 자체가 없다
 * (그쪽은 다른 패키지라 package-private 인 {@code post} 가 보이지 않는다).
 *
 * <p>HTTP 실행 자체(토큰 발급 락, 401 재발급, 429 백오프, {@code result} 언랩, 에러 매핑)는
 * {@code TossInvestClient} 것을 그대로 쓴다. 복제하면 규칙이 한쪽만 바뀌는 일이 생긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossOrderClient {

    private final TossInvestClient tossInvestClient;

    private static final String ORDERS_PATH = "/api/v1/orders";
    private static final String CANCEL_PATH_FORMAT = "/api/v1/orders/%s/cancel";

    /** 주문 생성 결과. */
    public record PlacedOrder(String orderId, String clientOrderId) { }

    /**
     * 주문에 필요한 값 묶음. 토스 요청 본문과 1:1 이지만 타입이 있는 형태로 받아,
     * 문자열 변환(토스는 모든 수치를 문자열로 받는다)을 이 클래스 안에 가둔다.
     *
     * @param timeInForce {@code null} 이면 토스 기본값 DAY. {@code CLS} + {@code LIMIT} 조합이 LOC 다.
     */
    public record TossOrderCommand(
            String clientOrderId,
            String symbol,
            String side,
            String orderType,
            String timeInForce,
            BigDecimal quantity,
            BigDecimal price,
            boolean confirmHighValueOrder
    ) { }

    /**
     * 주문을 낸다.
     *
     * <p>{@code clientOrderId} 는 <b>필수</b>다. 이 값이 있어야 401 재발급 재시도와 사용자의 수동 재시도가
     * 멱등해진다 — 없으면 재시도가 곧 두 번째 주문이다. 호출자가 반드시 채워서 넘기므로,
     * 비어 있다면 프로그래밍 오류로 보고 요청을 내보내지 않는다.
     */
    public PlacedOrder place(TossAccountOwner owner, Long accountSeq, TossOrderCommand command) {
        if (command.clientOrderId() == null || command.clientOrderId().isBlank()) {
            log.error("Refusing to place a Toss order without a clientOrderId (owner={}, symbol={})",
                    owner, command.symbol());
            throw new CustomException(ErrorCode.TOSS_ORDER_INVALID);
        }

        // 토스는 모든 수치를 문자열로 받는다. null 필드는 아예 빼야 한다 —
        // MARKET 주문에 price:null 을 실어 보내면 "가격 전달 불가"로 400 이 난다.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientOrderId", command.clientOrderId());
        body.put("symbol", command.symbol());
        body.put("side", command.side());
        body.put("orderType", command.orderType());
        if (command.timeInForce() != null && !command.timeInForce().isBlank()) {
            body.put("timeInForce", command.timeInForce());
        }
        body.put("quantity", command.quantity().stripTrailingZeros().toPlainString());
        if (command.price() != null) {
            body.put("price", command.price().stripTrailingZeros().toPlainString());
        }
        if (command.confirmHighValueOrder()) {
            body.put("confirmHighValueOrder", true);
        }

        JsonNode result = tossInvestClient.post(owner, ORDERS_PATH, body, accountSeq, true);

        String orderId = result.path("orderId").asString("");
        if (orderId.isBlank()) {
            // 2xx 인데 주문번호가 없으면 접수 여부를 알 수 없다. 성공으로 처리하면 화면이 거짓말을 한다.
            log.error("Toss order response has no orderId (owner={}, clientOrderId={})",
                    owner, command.clientOrderId());
            throw new CustomException(ErrorCode.TOSS_API_FAILED);
        }
        // 돈이 움직인 기록이다. 행위자는 MDC memberId, 주문 내용은 여기에 전부 남긴다.
        log.info("Placed Toss order (owner={}, symbol={}, side={}, orderType={}, timeInForce={}, quantity={}, price={}, clientOrderId={}, orderId={})",
                owner, command.symbol(), command.side(), command.orderType(), command.timeInForce(),
                command.quantity(), command.price(), command.clientOrderId(), orderId);
        return new PlacedOrder(orderId, result.path("clientOrderId").asString(command.clientOrderId()));
    }

    /**
     * 주문을 취소한다. 본문은 비어 있다(스펙상 optional).
     *
     * <p>취소는 재시도해도 결과가 같으므로(이미 취소된 주문은 토스가 거부한다) 429 재시도를 허용한다.
     */
    public String cancel(TossAccountOwner owner, Long accountSeq, String orderId) {
        String path = CANCEL_PATH_FORMAT.formatted(orderId);
        JsonNode result = tossInvestClient.post(owner, path, Map.of(), accountSeq, true);

        String canceledOrderId = result.path("orderId").asString("");
        log.info("Canceled Toss order (owner={}, orderId={})", owner, orderId);
        return canceledOrderId.isBlank() ? orderId : canceledOrderId;
    }
}
