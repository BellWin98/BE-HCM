package com.behcm.domain.tossstock.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 체결 내역으로부터 실현손익을 계산한다 (이동평균 원가법).
 *
 * <p>토스증권 Open API 에는 한국투자증권의 {@code inquire-period-trade-profit} 처럼
 * 서버가 실현손익을 계산해 주는 엔드포인트가 없다. 주문·체결 내역만 제공되므로 직접 계산한다.
 *
 * <p>계산 규칙:
 * <ul>
 *   <li>매수 원가에는 매수 수수료를 포함한다.</li>
 *   <li>매도 실수령액은 체결금액에서 수수료와 세금을 뺀 값이다.</li>
 *   <li>실현손익 = 매도 실수령액 − (이동평균 원가 × 매도수량).</li>
 *   <li>매도 후 잔량이 0이 되면 잔여 원가도 0으로 정리한다 — 반올림 잔재가 다음 평균단가를 오염시킨다.</li>
 * </ul>
 *
 * <p>계산은 계좌 개설 이후 전체 체결을 시간순으로 재생하는 것을 전제로 한다.
 * 그럼에도 매수 이력보다 앞선 매도가 나오면(이력 누락) 보유주식의 평균단가로 원가를 메우고
 * 해당 건을 {@code estimated} 로 표시한다 — 조용히 틀린 손익을 보여주는 것보다 낫다.
 */
@Component
public class TossRealizedProfitCalculator {

    public enum TradeSide { BUY, SELL }

    /**
     * 체결 1건. 금액은 모두 해당 종목의 거래통화 기준이다.
     */
    public record Fill(
            String symbol,
            String currency,
            TradeSide side,
            LocalDate tradeDate,
            BigDecimal quantity,
            BigDecimal amount,
            BigDecimal commission,
            BigDecimal tax
    ) { }

    /**
     * 체결 1건 + 계산된 실현손익.
     */
    public record RealizedFill(
            Fill fill,
            BigDecimal profitLoss,
            BigDecimal profitLossRate,
            boolean estimated
    ) { }

    /** 나눗셈 중간 정밀도. 평균단가가 반올림으로 뭉개지지 않도록 넉넉히 둔다. */
    private static final int INTERNAL_SCALE = 12;
    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final class Position {
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal cost = BigDecimal.ZERO;
    }

    /**
     * @param fills             체결 목록. <b>반드시 체결 시각 오름차순</b>이어야 한다.
     * @param seedAveragePrices 종목별 현재 보유 평균단가. 매수 이력이 없는 매도의 원가를 메우는 데만 쓴다.
     * @return 입력과 같은 순서의 계산 결과
     */
    public List<RealizedFill> calculate(List<Fill> fills, Map<String, BigDecimal> seedAveragePrices) {
        Map<String, Position> positions = new HashMap<>();
        List<RealizedFill> results = new ArrayList<>(fills.size());

        for (Fill fill : fills) {
            BigDecimal quantity = nonNull(fill.quantity());

            // 수량 0 인 체결은 계산할 것이 없다. 나눗셈을 태우면 0으로 나누게 된다.
            if (quantity.signum() <= 0) {
                results.add(new RealizedFill(fill, BigDecimal.ZERO, BigDecimal.ZERO, false));
                continue;
            }

            Position position = positions.computeIfAbsent(fill.symbol(), key -> new Position());

            if (fill.side() == TradeSide.BUY) {
                results.add(applyBuy(position, fill, quantity));
            } else {
                results.add(applySell(position, fill, quantity, seedAveragePrices));
            }
        }

        return results;
    }

    private RealizedFill applyBuy(Position position, Fill fill, BigDecimal quantity) {
        BigDecimal acquisitionCost = nonNull(fill.amount())
                .add(nonNull(fill.commission()))
                .add(nonNull(fill.tax()));

        position.quantity = position.quantity.add(quantity);
        position.cost = position.cost.add(acquisitionCost);

        // 매수 시점에는 실현손익이 발생하지 않는다.
        return new RealizedFill(fill, BigDecimal.ZERO, BigDecimal.ZERO, false);
    }

    private RealizedFill applySell(
            Position position,
            Fill fill,
            BigDecimal quantity,
            Map<String, BigDecimal> seedAveragePrices
    ) {
        boolean estimated = false;

        // 추적된 보유수량보다 많이 팔았다면 그만큼 매수 이력이 비어 있다는 뜻이다.
        if (quantity.compareTo(position.quantity) > 0) {
            BigDecimal shortfall = quantity.subtract(position.quantity);
            BigDecimal unitCost = seedAveragePrices.get(fill.symbol());
            if (unitCost == null) {
                // 평균단가조차 모르면 체결가를 원가로 간주한다. 실현손익이 0에 가깝게 잡히지만
                // 임의의 숫자를 지어내는 것보다는 낫고, estimated 로 화면에 드러난다.
                unitCost = nonNull(fill.amount()).divide(quantity, INTERNAL_SCALE, RoundingMode.HALF_UP);
            }
            position.cost = position.cost.add(unitCost.multiply(shortfall));
            position.quantity = quantity;
            estimated = true;
        }

        BigDecimal averageCost = position.quantity.signum() == 0
                ? BigDecimal.ZERO
                : position.cost.divide(position.quantity, INTERNAL_SCALE, RoundingMode.HALF_UP);
        BigDecimal costOfSold = averageCost.multiply(quantity);

        BigDecimal proceeds = nonNull(fill.amount())
                .subtract(nonNull(fill.commission()))
                .subtract(nonNull(fill.tax()));
        BigDecimal profitLoss = proceeds.subtract(costOfSold);
        BigDecimal profitLossRate = costOfSold.signum() == 0
                ? BigDecimal.ZERO
                : profitLoss.divide(costOfSold, INTERNAL_SCALE, RoundingMode.HALF_UP).multiply(HUNDRED);

        position.quantity = position.quantity.subtract(quantity);
        position.cost = position.cost.subtract(costOfSold);
        if (position.quantity.signum() <= 0) {
            position.quantity = BigDecimal.ZERO;
            position.cost = BigDecimal.ZERO;
        }

        return new RealizedFill(
                fill,
                profitLoss.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                profitLossRate.setScale(RATE_SCALE, RoundingMode.HALF_UP),
                estimated
        );
    }

    private BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
