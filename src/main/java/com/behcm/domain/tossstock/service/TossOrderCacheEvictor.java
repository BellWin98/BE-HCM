package com.behcm.domain.tossstock.service;

import com.behcm.global.config.toss.TossAccountOwner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

/**
 * 주문·취소 직후 그 계좌의 보유주식 캐시를 비운다.
 *
 * <p>별도 빈으로 둔 이유는 {@link TossHoldingsReader} 와 같다 — <b>같은 빈 안에서 호출하면
 * Spring 캐시 프록시를 타지 않아 {@code @CacheEvict} 가 조용히 무시된다.</b>
 * {@code TossOrderService} 가 이 빈을 주입받아 부르므로 프록시를 정상적으로 지난다.
 *
 * <p>지정가 주문은 즉시 체결되지 않을 수도 있지만 그래도 비운다. 즉시 체결될 수 있고,
 * 무엇보다 지금 능동적으로 거래 중인 사람에게 30초 묵은 잔고를 보여 주는 쪽이 더 나쁘다.
 *
 * <p><b>{@code tossOrderHistory} 는 비우지 않는다.</b> {@link TossOrderHistoryReader} 가 미체결
 * 주문을 워터마크로 삼아 매 조회마다 "변할 수 있는 구간"을 다시 읽는데, 방금 낸 주문의 접수일은
 * 오늘이라 그 구간에 반드시 들어온다 — 즉 비우지 않아도 다음 조회에서 잡힌다. 반대로 비우면
 * 스냅샷이 사라져 다음 조회가 계좌 전체를 다시 페이징한다. 하필 주문 직후가 수익분석을 가장 많이
 * 보는 시점이라, 비우는 쪽이 손해다.
 *
 * <p>새 캐시를 만들지 않는다 — 이름은 {@code CacheConfig} 에 이미 등록되어 있다.
 * 등록되지 않은 이름으로 evict 하면 {@code SimpleCacheManager} 가 예외를 던져
 * <b>주문은 성공했는데 응답은 실패</b>가 되는 최악의 조합이 나온다.
 */
@Slf4j
@Component
public class TossOrderCacheEvictor {

    @CacheEvict(value = "tossHoldings", key = "#owner")
    public void evictAccountCaches(TossAccountOwner owner) {
        log.debug("Evicted the Toss holdings cache (owner={})", owner);
    }
}
