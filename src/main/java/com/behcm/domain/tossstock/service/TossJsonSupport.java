package com.behcm.domain.tossstock.service;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 토스증권 응답 파싱 헬퍼.
 *
 * <p>토스는 모든 수치를 <b>문자열</b>로 주고, 값이 없는 필드는 <b>JSON null</b>로 준다
 * (해외 종목이 없으면 {@code usd}가 null, 세금이 없으면 {@code tax}가 null 등).
 * 손익률은 퍼센트가 아니라 <b>소수비율</b>(0.1516 = 15.16%)이라 화면에 그대로 쓰면 100배 작게 표시된다.
 */
@Slf4j
final class TossJsonSupport {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int RATE_SCALE = 2;

    private TossJsonSupport() {
    }

    /**
     * 숫자 필드를 읽는다. 없거나 null 이거나 파싱 불가면 0.
     */
    static BigDecimal decimal(JsonNode node, String fieldName) {
        BigDecimal value = nullableDecimal(node, fieldName);
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 숫자 필드를 읽되 값이 없으면 null 을 유지한다.
     * "해외 종목이 없으면 null" 같은 필드에서 0 과 미보유를 구분하기 위한 것이다.
     */
    static BigDecimal nullableDecimal(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode field = node.path(fieldName);
        if (field.isMissingNode() || field.isNull()) {
            return null;
        }
        String raw = field.asString("").trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            log.warn("Unparsable Toss numeric field {}='{}', defaulting to null", fieldName, raw);
            return null;
        }
    }

    /**
     * 소수비율로 오는 손익률을 퍼센트로 변환한다. 0.1516 -> 15.16
     */
    static BigDecimal percent(JsonNode node, String fieldName) {
        BigDecimal ratio = nullableDecimal(node, fieldName);
        if (ratio == null) {
            return BigDecimal.ZERO;
        }
        return ratio.multiply(HUNDRED).setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }
}
