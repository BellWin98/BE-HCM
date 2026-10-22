package com.behcm.domain.tossstock.service;

import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestClient;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * 계좌의 시장별 매매 수수료율({@code GET /api/v1/commissions}).
 *
 * <p>주문 시트의 예상 수수료에 쓴다. 지금까지는 보유 종목의 비용 추정치(수수료+세금 ÷ 평가금액)로만 비용을
 * 냈는데, 그 값은 <b>보유 중인 종목의 매도</b>에만 있다. 수수료율이 있으면 매수와 미보유 종목에도 예상
 * 수수료를 보여 줄 수 있다. 세율은 여기 없다 — 세금은 여전히 보유 비용 추정치에서만 나온다.
 *
 * <p><b>소유자별로 캐시한다.</b> 요율은 계좌마다 다르고(우대 이벤트) 적용 기간 단위로 바뀌어 거의 변하지
 * 않는다. 주문 시트를 열 때마다 묻지 않도록 {@code tossCommission} 에 6시간 둔다. 캐시를 이 컴포넌트에 두는
 * 이유는 {@link TossExchangeRateReader} 와 같다 — 같은 빈 안에서 부르면 캐시 프록시를 타지 않는다.
 */
@Component
@RequiredArgsConstructor
public class TossCommissionReader {

    private final TossInvestClient tossInvestClient;

    private static final String COMMISSIONS_PATH = "/api/v1/commissions";

    /** 계좌의 수수료율 목록(시장별). accountSeq 는 소유자가 정하므로 키는 소유자만으로 충분하다. */
    @Cacheable(value = "tossCommission", key = "#owner.name()")
    public JsonNode read(TossAccountOwner owner, Long accountSeq) {
        return tossInvestClient.get(owner, COMMISSIONS_PATH, Map.of(), accountSeq);
    }

    /**
     * 그 시장에 <b>오늘</b> 적용되는 요율. 적용 기간({@code startDate}~{@code endDate}, 경계 포함, null 은 열림)이
     * 오늘을 포함하는 항목만 본다. 끝난 우대 요율을 쓰면 예상 수수료가 실제보다 작게 보이므로, 맞는 항목이 없으면
     * null 이다 — 화면은 null 이면 예상 수수료를 그리지 않는다.
     */
    static BigDecimal rateFor(JsonNode commissions, String marketCountry, LocalDate today) {
        if (commissions == null || !commissions.isArray()) {
            return null;
        }
        for (JsonNode entry : commissions) {
            if (!marketCountry.equals(entry.path("marketCountry").asString(""))) {
                continue;
            }
            LocalDate start = date(entry, "startDate");
            LocalDate end = date(entry, "endDate");
            boolean started = start == null || !today.isBefore(start);
            boolean notEnded = end == null || !today.isAfter(end);
            if (started && notEnded) {
                BigDecimal rate = TossJsonSupport.nullableDecimal(entry, "commissionRate");
                if (rate != null) {
                    return rate;
                }
            }
        }
        return null;
    }

    private static LocalDate date(JsonNode entry, String field) {
        String value = entry.path(field).asString("");
        if (value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
