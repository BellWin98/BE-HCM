package com.behcm.domain.tossstock.service;

import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestClient;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.Map;

/**
 * 소유자를 토스증권 {@code accountSeq} 로 변환한다.
 *
 * <p>{@code accountSeq} 는 보유주식·주문내역·매수가능금액 등 계좌 컨텍스트가 필요한 모든 API 의
 * {@code X-Tossinvest-Account} 헤더 값이다. 거의 바뀌지 않으므로 길게 캐시한다.
 *
 * <p>프론트가 accountSeq 를 직접 넘기게 하지 않고 서버에서 소유자→계좌를 강제한다 —
 * 그렇지 않으면 값만 바꿔 남의 계좌를 조회할 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TossAccountResolver {

    private final TossInvestClient tossInvestClient;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String ACCOUNTS_PATH = "/api/v1/accounts";
    private static final String CACHE_KEY_PREFIX = "toss_invest:account_seq:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final String BROKERAGE = "BROKERAGE";

    public Long resolveAccountSeq(TossAccountOwner owner) {
        Object cached = redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + owner.name());
        if (cached instanceof Number number) {
            return number.longValue();
        }

        Long accountSeq = fetchAccountSeq(owner);
        redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + owner.name(), accountSeq, CACHE_TTL);
        return accountSeq;
    }

    private Long fetchAccountSeq(TossAccountOwner owner) {
        JsonNode accounts = tossInvestClient.get(owner, ACCOUNTS_PATH, Map.of());

        if (!accounts.isArray() || accounts.isEmpty()) {
            log.error("Toss returned no accounts for owner={}", owner);
            throw new CustomException(ErrorCode.TOSS_ACCOUNT_NOT_FOUND);
        }

        // 현재 스펙상 BROKERAGE 만 노출되지만, 다른 유형이 추가되어도 종합매매 계좌를 고르도록 명시한다.
        for (JsonNode account : accounts) {
            if (BROKERAGE.equals(account.path("accountType").asString(""))) {
                long accountSeq = account.path("accountSeq").asLong(-1L);
                if (accountSeq >= 0) {
                    return accountSeq;
                }
            }
        }

        log.error("Toss returned no BROKERAGE account for owner={}", owner);
        throw new CustomException(ErrorCode.TOSS_ACCOUNT_NOT_FOUND);
    }
}
