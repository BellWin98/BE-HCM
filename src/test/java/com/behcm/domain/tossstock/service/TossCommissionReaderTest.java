package com.behcm.domain.tossstock.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계좌 수수료율 중 "이 종목에 오늘 적용되는 값" 고르기.
 *
 * <p>토스는 시장별 수수료율을 적용 기간과 함께 준다(이벤트 우대 요율은 종료일이 있다).
 * 끝난 우대 요율을 그대로 쓰면 예상 수수료가 실제보다 작게 보인다 — 모르면 null 이 낫다.
 */
class TossCommissionReaderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode json(String text) {
        return objectMapper.readTree(text);
    }

    @Test
    @DisplayName("종목 시장의 요율을 고른다")
    void picksRateForMarket() {
        JsonNode rates = json("""
                [{"marketCountry":"KR","commissionRate":"0.00015","startDate":"2026-01-01","endDate":null},
                 {"marketCountry":"US","commissionRate":"0.001","startDate":null,"endDate":null}]
                """);

        assertThat(TossCommissionReader.rateFor(rates, "KR", TODAY)).isEqualByComparingTo("0.00015");
        assertThat(TossCommissionReader.rateFor(rates, "US", TODAY)).isEqualByComparingTo("0.001");
    }

    @Test
    @DisplayName("적용 기간이 오늘을 포함하는 요율만 쓴다 — 경계일은 포함")
    void usesOnlyRatesActiveToday() {
        JsonNode rates = json("""
                [{"marketCountry":"US","commissionRate":"0.001","startDate":null,"endDate":"2026-10-21"},
                 {"marketCountry":"US","commissionRate":"0.0025","startDate":"2026-10-22","endDate":null}]
                """);

        assertThat(TossCommissionReader.rateFor(rates, "US", TODAY)).isEqualByComparingTo("0.0025");
    }

    @Test
    @DisplayName("오늘 유효한 요율이 없으면 null 이다 — 끝난 우대 요율로 수수료를 작게 보여주지 않는다")
    void returnsNullWhenNoRateIsActive() {
        JsonNode rates = json("""
                [{"marketCountry":"US","commissionRate":"0.001","startDate":null,"endDate":"2026-06-30"}]
                """);

        assertThat(TossCommissionReader.rateFor(rates, "US", TODAY)).isNull();
    }

    @Test
    @DisplayName("응답이 없거나 해당 시장이 없으면 null 이다")
    void returnsNullForMissingMarketOrResponse() {
        assertThat(TossCommissionReader.rateFor(null, "KR", TODAY)).isNull();
        assertThat(TossCommissionReader.rateFor(json("[]"), "KR", TODAY)).isNull();
        assertThat(TossCommissionReader.rateFor(json("""
                [{"marketCountry":"US","commissionRate":"0.001"}]
                """), "KR", TODAY)).isNull();
    }
}
