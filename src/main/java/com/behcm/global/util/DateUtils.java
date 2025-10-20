package com.behcm.global.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

// final 키워드: 상속 방지
public final class DateUtils {

    // private 생성자: 인스턴스화 방지
    private DateUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean isThisWeek(LocalDate targetDate) {
        if (targetDate == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        return !targetDate.isBefore(startOfWeek) && !targetDate.isAfter(endOfWeek);
    }

    public static LocalDate getWeekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static LocalDate getWeekEnd(LocalDate date) {
        return date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }

    public static LocalDate getLastWeekStart() {
        LocalDate today = LocalDate.now();
        return today.minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static LocalDate getLastWeekEnd() {
        return getLastWeekStart().plusDays(6);
    }

    public static boolean isDateInRange(LocalDate targetDate, LocalDate startDate, LocalDate endDate) {
        return !(targetDate.isBefore(startDate) || targetDate.isAfter(endDate));
    }
}