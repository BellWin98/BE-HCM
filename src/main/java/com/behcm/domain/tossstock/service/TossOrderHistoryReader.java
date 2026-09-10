package com.behcm.domain.tossstock.service;

import com.behcm.domain.tossstock.service.TossRealizedProfitCalculator.Fill;
import com.behcm.domain.tossstock.service.TossRealizedProfitCalculator.TradeSide;
import com.behcm.global.config.toss.TossAccountOwner;
import com.behcm.global.config.toss.TossInvestClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 종료된 주문의 체결 내역을 계좌 전체 기간에 대해 들고 있는다.
 *
 * <p>실현손익을 이동평균 원가법으로 계산하려면 <b>계좌 개설 이후 전체 체결</b>을 시간순으로 재생해야
 * 한다. 조회 기간만 잘라 오면 그 이전에 매수한 물량의 원가를 알 수 없다. 그래서 전체를 스냅샷으로
 * 들고 있고, 기간 필터는 계산 결과에 적용한다.
 *
 * <h2>매번 전체를 다시 읽지 않는 방법</h2>
 *
 * <p>전체 페이징은 {@code ceil(전체 주문 / 100)} 회의 <b>순차</b> 왕복이고, 토스 주문내역은 앱에서 낸
 * 주문까지 전부 반환하므로 계좌가 오래될수록 계속 비싸진다. 그래서 스냅샷을 캐시하고 <b>변할 수 있는
 * 구간만</b> 다시 읽는다.
 *
 * <p>어디까지가 "변할 수 있는 구간"인지는 <b>미체결 목록</b>이 알려준다({@code TossOpenOrderReader
 * #earliestOrderedAt}). 종료된 주문 목록은 접수 시각({@code orderedAt}) 축으로 정렬·필터되는데,
 * 지정가는 접수가 과거인 채로 나중에 체결되므로 "가장 최근 주문만 비교해서 바뀌었는지 본다"는 방법은
 * 성립하지 않는다 — 새 체결이 목록 맨 앞이 아니라 <b>중간에 삽입</b>된다. 대신 <b>살아 있지 않으면
 * 확정</b>이라는 상태 기준을 쓴다. 이 방식은 응답 정렬 순서를 신뢰하지 않아도 된다.
 *
 * <p>캐시 TTL 은 정합성 장치가 아니라 자가 치유 안전망일 뿐이다. 정합성은 이 워터마크가 지킨다 —
 * 애초에 주문 직후 캐시를 비우는 방식으로는 부족하다. 우리 서버는 우리를 거친 주문만 알 수 있는데,
 * 토스 주문내역에는 <b>앱에서 낸 주문까지</b> 들어오기 때문이다. 반대로 비울 이유도 없다 —
 * 우리 서버로 낸 주문도 접수일이 오늘이라 재조회 구간에 이미 포함되므로,
 * 비워 봐야 전체 페이징만 한 번 더 하게 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossOrderHistoryReader {

    private final TossInvestClient tossInvestClient;
    private final TossOpenOrderReader openOrderReader;
    private final CacheManager cacheManager;

    private static final String ORDERS_PATH = "/api/v1/orders";

    /** {@code CacheConfig} 에 등록된 이름. 스냅샷을 직접 넣고 빼므로 {@code @Cacheable} 은 쓰지 않는다. */
    static final String CACHE_NAME = "tossOrderHistory";

    /** 토스 주문 목록의 페이지 크기 상한. */
    private static final int PAGE_SIZE = 100;
    /** 커서가 끝나지 않는 경우에도 호출이 유한하도록 상한을 둔다. */
    static final int MAX_ORDER_PAGES = 100;

    /**
     * 미체결 목록을 못 구했을 때 되돌아갈 재조회 창(일).
     *
     * <p>{@code timeInForce} 는 {@code DAY}/{@code CLS}/{@code OPG} 뿐이라 주문이 당일 세션을 넘겨
     * 살아남지 못한다. 다만 미국장은 KST 날짜를 넘기므로(22:00 접수 → 익일 01:00 체결) 최소 2일이
     * 필요하고, 주말·휴장 여유로 3일을 잡는다.
     */
    static final int SAFETY_MARGIN_DAYS = 3;

    /**
     * 체결 목록(시간 오름차순)과 종목명 매핑.
     * 주문 응답에는 종목명이 없어 별도로 채운다.
     */
    public record OrderHistory(List<Fill> fills, Map<String, String> names) { }

    /**
     * 캐시에 담는 체결 1건. {@code orderId} 를 함께 들고 있는 이유는 <b>병합이 덧붙이기가 아니라
     * 교체</b>여야 하기 때문이다 — {@code PARTIAL_FILLED} 는 미체결·종료 두 그룹에 모두 속해서 같은
     * 주문의 체결 수량이 나중에 늘어난다. 덧붙이면 같은 체결이 두 번 계산되어 손익이 두 배가 된다.
     *
     * <p>{@code orderedAt} 은 재조회 창의 경계 판정에 쓴다(체결 시각이 아니다 — API 의 필터 축이
     * 접수 시각이다).
     */
    private record OrderFill(String orderId, LocalDateTime orderedAt, Fill fill) { }

    /**
     * 캐시 엔트리. 체결·종목명·동기화 시각을 <b>한 객체</b>로 묶는 것이 중요하다 — 따로 캐시하면
     * 한쪽만 남아 영구히 어긋난 상태가 생길 수 있다. 하나면 evict 가 구조적으로 원자적이다.
     */
    private record Snapshot(Map<String, OrderFill> byOrderId, Map<String, String> names, LocalDateTime syncedAt) { }

    private record PageStats(int pages, int fills) { }

    /**
     * 소유자별 동기화 직렬화. 동시에 두 요청이 들어오면 각각 전체 페이징을 도는 것을 막는다
     * ({@code @Cacheable(sync = true)} 로 얻던 보호를 대신한다). 소유자는 가족 몇 명뿐이다.
     */
    private final ConcurrentMap<TossAccountOwner, Object> syncLocks = new ConcurrentHashMap<>();

    public OrderHistory readAll(TossAccountOwner owner, Long accountSeq) {
        synchronized (syncLocks.computeIfAbsent(owner, key -> new Object())) {
            Cache cache = cacheManager.getCache(CACHE_NAME);
            Snapshot cached = cache == null ? null : cache.get(owner, Snapshot.class);

            // 조회를 시작하기 <b>전에</b> 찍는다. 읽는 도중 들어온 체결은 다음 회차의 창에 반드시
            // 포함되어야 한다 — 순서를 뒤집으면 그 사이 체결을 영영 놓친다.
            LocalDateTime syncStartedAt = LocalDateTime.now();

            Snapshot synced = cached == null
                    ? fullSync(owner, accountSeq, syncStartedAt)
                    : incrementalSync(owner, accountSeq, cached, syncStartedAt);

            if (cache != null) {
                cache.put(owner, synced);
            }
            return toHistory(synced);
        }
    }

    /** 스냅샷이 없을 때. 기간 필터 없이 전부 읽는다. */
    private Snapshot fullSync(TossAccountOwner owner, Long accountSeq, LocalDateTime syncedAt) {
        long startedAt = System.nanoTime();

        Map<String, OrderFill> byOrderId = new LinkedHashMap<>();
        PageStats stats = readClosedOrders(owner, accountSeq, null, byOrderId);
        Map<String, String> names = resolveNames(owner, Map.of(), byOrderId);

        log.info("Toss order history sync: owner={}, mode=full, pages={}, fills={}, took={}ms",
                owner, stats.pages(), byOrderId.size(), elapsedMs(startedAt));
        return new Snapshot(byOrderId, names, syncedAt);
    }

    /** 스냅샷이 있을 때. 워터마크 이후 접수분만 다시 읽어 교체한다. */
    private Snapshot incrementalSync(
            TossAccountOwner owner, Long accountSeq, Snapshot cached, LocalDateTime syncedAt) {
        long startedAt = System.nanoTime();
        LocalDate from = refetchFrom(owner, accountSeq, cached);

        // 창 이전 접수분은 더 이상 변하지 않는다. 창 안쪽은 새 응답이 권위이므로 통째로 버리고 다시 채운다.
        Map<String, OrderFill> merged = new LinkedHashMap<>();
        for (OrderFill entry : cached.byOrderId().values()) {
            if (entry.orderedAt().toLocalDate().isBefore(from)) {
                merged.put(entry.orderId(), entry);
            }
        }
        int reused = merged.size();

        PageStats stats = readClosedOrders(owner, accountSeq, from, merged);
        Map<String, String> names = resolveNames(owner, cached.names(), merged);

        log.info("Toss order history sync: owner={}, mode=incremental, from={}, pages={}, "
                        + "reused={}, refetched={}, fills={}, took={}ms",
                owner, from, stats.pages(), reused, stats.fills(), merged.size(), elapsedMs(startedAt));
        return new Snapshot(merged, names, syncedAt);
    }

    /**
     * 다시 읽어야 하는 구간의 시작일.
     *
     * <p>둘 중 이른 쪽이다 — 아직 살아 있는 주문의 접수일(지금 체결될 수 있다), 그리고 직전 동기화
     * 시각에서 안전 여유를 뺀 날(그 사이 새로 접수된 주문을 반드시 덮는다).
     */
    private LocalDate refetchFrom(TossAccountOwner owner, Long accountSeq, Snapshot cached) {
        LocalDate marginFloor = cached.syncedAt().toLocalDate().minusDays(SAFETY_MARGIN_DAYS);
        try {
            return openOrderReader.earliestOrderedAt(owner, accountSeq)
                    .map(LocalDateTime::toLocalDate)
                    .filter(openFloor -> openFloor.isBefore(marginFloor))
                    .orElse(marginFloor);
        } catch (Exception e) {
            // 워터마크를 못 구했다고 전체를 다시 읽을 이유는 없다. 보수적인 창으로 계속 간다 —
            // 종목명 조회 실패를 삼키는 것과 같은 판단이다(본 데이터는 여전히 유효하다).
            log.warn("Failed to read Toss open orders for the sync watermark (owner={}); "
                    + "falling back to a {}-day window", owner, SAFETY_MARGIN_DAYS, e);
            return marginFloor;
        }
    }

    /**
     * 종료된 주문을 페이징하며 {@code target} 에 채운다.
     *
     * @param from 접수일 하한(inclusive). null 이면 전체 기간.
     */
    private PageStats readClosedOrders(
            TossAccountOwner owner, Long accountSeq, LocalDate from, Map<String, OrderFill> target) {
        String cursor = null;
        int pages = 0;
        int fills = 0;

        for (int page = 0; page < MAX_ORDER_PAGES; page++) {
            Map<String, String> params = new HashMap<>();
            params.put("status", "CLOSED");
            params.put("limit", String.valueOf(PAGE_SIZE));
            if (from != null) {
                params.put("from", from.toString());
            }
            if (cursor != null) {
                params.put("cursor", cursor);
            }

            long pageStartedAt = System.nanoTime();
            JsonNode result = tossInvestClient.get(owner, ORDERS_PATH, params, accountSeq);
            pages++;

            JsonNode orders = result.path("orders");
            if (orders.isArray()) {
                for (JsonNode order : orders) {
                    OrderFill fill = toOrderFill(order);
                    if (fill != null) {
                        // 덧붙이지 않고 교체한다. OrderFill javadoc 참조 — 여기가 손익 두 배의 갈림길이다.
                        target.put(fill.orderId(), fill);
                        fills++;
                    }
                }
            }
            log.debug("Toss order page {} took {}ms (owner={}, from={})",
                    page, elapsedMs(pageStartedAt), owner, from);

            if (!result.path("hasNext").asBoolean(false)) {
                break;
            }
            cursor = result.path("nextCursor").asString("");
            if (cursor.isBlank()) {
                break;
            }

            if (page == MAX_ORDER_PAGES - 1) {
                log.warn("Toss order history hit the {}-page cap (owner={}); realized profit may be incomplete",
                        MAX_ORDER_PAGES, owner);
            }
        }
        return new PageStats(pages, fills);
    }

    /** 계산에 쓸 수 없는 주문이면 null. */
    private OrderFill toOrderFill(JsonNode order) {
        JsonNode execution = order.path("execution");
        BigDecimal filledQuantity = TossJsonSupport.decimal(execution, "filledQuantity");

        // 취소·거부된 주문도 CLOSED 에 포함된다. 체결이 없으면 손익과 무관하다.
        if (filledQuantity.signum() <= 0) {
            return null;
        }

        // orderId 가 없으면 교체 기준을 세울 수 없다. 스펙상 필수 필드이므로 오면 이상 신호다.
        String orderId = order.path("orderId").asString("");
        if (orderId.isBlank()) {
            log.warn("Skipping Toss order without an orderId: symbol={}", order.path("symbol").asString(""));
            return null;
        }

        String side = order.path("side").asString("");
        TradeSide tradeSide = "SELL".equals(side) ? TradeSide.SELL : "BUY".equals(side) ? TradeSide.BUY : null;
        if (tradeSide == null) {
            log.warn("Skipping Toss order with unknown side: {}", side);
            return null;
        }

        LocalDateTime orderedAt = TossJsonSupport.dateTime(order, "orderedAt");

        // 체결 시각이 없으면 주문 시각으로 대체한다(순서 결정과 화면의 거래일시 표기에 쓴다).
        LocalDateTime executedAt = TossJsonSupport.dateTime(execution, "filledAt");
        if (executedAt == null) {
            executedAt = orderedAt;
        }
        if (executedAt == null) {
            log.warn("Skipping Toss order without a usable timestamp: symbol={}", order.path("symbol").asString(""));
            return null;
        }

        // 접수 시각이 없으면 재조회 창 경계를 판정할 수 없다. 체결 시각으로 대신하면 창이 좁아질 뿐
        // 넓어지지는 않으므로(체결은 접수보다 늦다) 누락 방향으로 틀리지 않는다.
        if (orderedAt == null) {
            orderedAt = executedAt;
        }

        Fill fill = new Fill(
                order.path("symbol").asString(""),
                order.path("currency").asString("KRW"),
                tradeSide,
                executedAt,
                filledQuantity,
                TossJsonSupport.decimal(execution, "filledAmount"),
                TossJsonSupport.decimal(execution, "commission"),
                TossJsonSupport.decimal(execution, "tax")
        );
        return new OrderFill(orderId, orderedAt, fill);
    }

    /**
     * 아직 이름을 모르는 심볼만 채운다. 주문 응답에는 종목명이 없어 별도 조회가 필요한데,
     * 증분 동기화에서 매번 전 종목을 다시 물으면 아낀 호출이 도로 나간다.
     *
     * <p>미체결 목록도 같은 사정이라 배치·실패 처리 규칙은 {@link TossStockNameResolver} 한 곳에 있다.
     */
    private Map<String, String> resolveNames(
            TossAccountOwner owner, Map<String, String> known, Map<String, OrderFill> byOrderId) {
        List<String> unknown = byOrderId.values().stream()
                .map(entry -> entry.fill().symbol())
                .distinct()
                .filter(symbol -> !known.containsKey(symbol))
                .toList();
        if (unknown.isEmpty()) {
            return known;
        }

        Map<String, String> names = new HashMap<>(known);
        names.putAll(TossStockNameResolver.resolve(tossInvestClient, owner, unknown));
        // 이 맵은 캐시된 스냅샷과 응답이 함께 참조한다. 불변으로 굳혀 두면 한쪽에서 건드릴 수 없다.
        return Map.copyOf(names);
    }

    /**
     * 이동평균 원가는 순서에 의존한다. 스냅샷은 주문 단위 맵이므로 여기서 체결 시각 오름차순으로 편다
     * — API 정렬은 신뢰하지 않는다.
     */
    private OrderHistory toHistory(Snapshot snapshot) {
        List<Fill> fills = snapshot.byOrderId().values().stream()
                .map(OrderFill::fill)
                .sorted(Comparator.comparing(Fill::executedAt))
                .toList();
        return new OrderHistory(fills, snapshot.names());
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
