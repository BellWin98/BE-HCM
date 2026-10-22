package com.behcm.domain.tossstock.service;

import com.behcm.domain.tossstock.service.TossOrderHistorySnapshot.OrderFill;
import com.behcm.domain.tossstock.service.TossRealizedProfitCalculator.Fill;
import com.behcm.domain.tossstock.service.TossRealizedProfitCalculator.TradeSide;
import com.behcm.global.config.toss.TossAccountOwner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 주문내역 스냅샷을 메모리(L1)와 Redis(L2)에 둔다.
 *
 * <p><b>왜 Redis 인가</b>: 스냅샷이 없으면 계좌 개설 이후 전체 주문을 {@code ceil(주문 수 / 100)} 회 순차로
 * 페이징해야 하고, 토스 주문내역은 앱 주문까지 돌려주므로 계좌가 오래될수록 비싸진다. 메모리에만 두면
 * <b>배포·재시작 때마다</b> 그 비용을 처음 요청한 사용자가 떠안는다. Redis 에 두면 재시작 후에도 증분
 * 동기화로 바로 이어간다.
 *
 * <p><b>메모리를 앞에 두는 이유</b>: 스냅샷은 수천 건 체결이 담긴 큰 값이라 요청마다 Redis 에서 읽어
 * 역직렬화하면 그 자체가 비용이다. 같은 인스턴스 안에서는 메모리가 권위이고, Redis 는 그 사본이다.
 *
 * <p><b>Redis 는 보조 수단이다</b>: 읽기·쓰기가 실패해도 예외를 내보내지 않는다 — 최악은 "재시작 뒤 한 번
 * 전체 페이징"이지 화면 장애가 아니다. 같은 이유로 읽을 수 없는 값·형식 버전이 다른 값은 없는 것으로 본다.
 * 옛 값을 억지로 해석해 틀린 원가로 손익을 내는 것보다 다시 읽는 편이 낫다.
 *
 * <p>Redis 값은 도메인 레코드를 그대로 직렬화하지 않고 {@link Stored} 라는 별도 형식으로 쓴다.
 * 도메인 레코드를 고칠 때 저장 형식이 조용히 바뀌지 않게 하려는 것이고, 의미가 바뀌면
 * {@link #FORMAT_VERSION} 을 올린다.
 */
@Slf4j
@Component
public class TossOrderHistoryStore {

    /** {@code CacheConfig} 에 등록된 메모리 캐시 이름. */
    static final String CACHE_NAME = "tossOrderHistory";

    /**
     * Redis 사본의 수명. 정합성은 증분 동기화와 매일 밤 재적재({@code TossOrderHistoryRefresher})가 지키고,
     * 이 값은 어긋난 사본이 영구히 남지 않게 하는 안전망이다. 재적재가 하루 걸러도 살아남도록 이틀로 잡는다.
     */
    static final Duration REDIS_TTL = Duration.ofHours(48);

    /** 저장 형식 버전. {@link Stored} 의 필드 의미가 바뀌면 올린다 — 옛 값은 자동으로 버려진다. */
    static final int FORMAT_VERSION = 1;

    private static final String KEY_PREFIX = "toss_invest:order_history:";

    private final CacheManager cacheManager;
    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper = new JsonMapper();

    public TossOrderHistoryStore(CacheManager cacheManager, StringRedisTemplate redisTemplate) {
        this.cacheManager = cacheManager;
        this.redisTemplate = redisTemplate;
    }

    /** Redis 에 쓰는 형식. 도메인 레코드와 분리해 둔다(클래스 javadoc 참조). */
    record Stored(int version, LocalDateTime syncedAt, Map<String, String> names, List<StoredFill> fills) { }

    record StoredFill(
            String orderId,
            LocalDateTime orderedAt,
            String symbol,
            String currency,
            TradeSide side,
            LocalDateTime executedAt,
            BigDecimal quantity,
            BigDecimal amount,
            BigDecimal commission,
            BigDecimal tax
    ) { }

    Optional<TossOrderHistorySnapshot> load(TossAccountOwner owner) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        TossOrderHistorySnapshot inMemory = cache == null ? null : cache.get(owner, TossOrderHistorySnapshot.class);
        if (inMemory != null) {
            return Optional.of(inMemory);
        }

        Optional<TossOrderHistorySnapshot> restored = readRedis(owner);
        // 되살린 값은 메모리에도 올린다. 다음 요청부터는 역직렬화 비용이 없다.
        restored.ifPresent(snapshot -> {
            if (cache != null) {
                cache.put(owner, snapshot);
            }
        });
        return restored;
    }

    void save(TossAccountOwner owner, TossOrderHistorySnapshot snapshot) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.put(owner, snapshot);
        }
        writeRedis(owner, snapshot);
    }

    private Optional<TossOrderHistorySnapshot> readRedis(TossAccountOwner owner) {
        String json;
        try {
            json = redisTemplate.opsForValue().get(KEY_PREFIX + owner.name());
        } catch (Exception e) {
            log.warn("Failed to read the Toss order history snapshot from Redis (owner={}); treating it as missing",
                    owner, e);
            return Optional.empty();
        }
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }

        Stored stored;
        try {
            stored = jsonMapper.readValue(json, Stored.class);
        } catch (Exception e) {
            log.warn("Unreadable Toss order history snapshot in Redis (owner={}); falling back to a full sync", owner, e);
            return Optional.empty();
        }
        if (stored.version() != FORMAT_VERSION || stored.syncedAt() == null || stored.fills() == null) {
            log.info("Discarding a Toss order history snapshot in format v{} (owner={}, current=v{})",
                    stored.version(), owner, FORMAT_VERSION);
            return Optional.empty();
        }

        log.info("Restored the Toss order history snapshot from Redis (owner={}, fills={}, syncedAt={})",
                owner, stored.fills().size(), stored.syncedAt());
        return Optional.of(toSnapshot(stored));
    }

    private void writeRedis(TossAccountOwner owner, TossOrderHistorySnapshot snapshot) {
        try {
            String json = jsonMapper.writeValueAsString(toStored(snapshot));
            redisTemplate.opsForValue().set(KEY_PREFIX + owner.name(), json, REDIS_TTL);
        } catch (Exception e) {
            // 메모리에는 이미 들어갔다. 사본을 못 남긴 대가는 재시작 뒤 한 번의 전체 페이징뿐이다.
            log.warn("Failed to write the Toss order history snapshot to Redis (owner={})", owner, e);
        }
    }

    private static Stored toStored(TossOrderHistorySnapshot snapshot) {
        List<StoredFill> fills = snapshot.byOrderId().values().stream()
                .map(entry -> {
                    Fill fill = entry.fill();
                    return new StoredFill(entry.orderId(), entry.orderedAt(), fill.symbol(), fill.currency(),
                            fill.side(), fill.executedAt(), fill.quantity(), fill.amount(),
                            fill.commission(), fill.tax());
                })
                .toList();
        return new Stored(FORMAT_VERSION, snapshot.syncedAt(), snapshot.names(), fills);
    }

    private static TossOrderHistorySnapshot toSnapshot(Stored stored) {
        Map<String, OrderFill> byOrderId = new LinkedHashMap<>();
        for (StoredFill fill : stored.fills()) {
            byOrderId.put(fill.orderId(), new OrderFill(fill.orderId(), fill.orderedAt(), new Fill(
                    fill.symbol(), fill.currency(), fill.side(), fill.executedAt(),
                    fill.quantity(), fill.amount(), fill.commission(), fill.tax())));
        }
        // 스냅샷의 종목명 맵은 응답과 공유되므로 불변으로 굳힌다(TossOrderHistoryReader#resolveNames 와 같은 이유).
        Map<String, String> names = stored.names() == null ? Map.of() : Map.copyOf(stored.names());
        return new TossOrderHistorySnapshot(byOrderId, names, stored.syncedAt());
    }
}
