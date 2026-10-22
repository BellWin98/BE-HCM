package com.behcm.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * 테스트가 시간을 직접 움직이는 시계.
 *
 * <p>{@code autoStep} 을 주면 읽을 때마다 그만큼 저절로 흐른다 — "호출 사이에 시간이 지났다"를
 * 전제로 쓴 테스트가 매번 {@link #advance} 를 부르지 않아도 되게 하려는 것이다.
 */
public final class MutableClock extends Clock {

    private final ZoneId zone;
    private final Duration autoStep;
    private Instant now;

    public MutableClock(Instant start, ZoneId zone, Duration autoStep) {
        this.now = start;
        this.zone = zone;
        this.autoStep = autoStep;
    }

    /** 멈춰 있는 시계. {@link #advance} 로만 움직인다. */
    public static MutableClock fixedAtNow() {
        return new MutableClock(Instant.now(), ZoneId.systemDefault(), Duration.ZERO);
    }

    /** 읽을 때마다 {@code step} 만큼 흐르는 시계. */
    public static MutableClock tickingFromNow(Duration step) {
        return new MutableClock(Instant.now(), ZoneId.systemDefault(), step);
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(now, zone, autoStep);
    }

    @Override
    public Instant instant() {
        Instant current = now;
        now = now.plus(autoStep);
        return current;
    }
}
