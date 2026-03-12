package com.behcm.domain.workout.enums;

public enum PeriodType {

    ALL,
    WEEK,
    MONTH;

    public static PeriodType from(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }

        try {
            return PeriodType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ALL;
        }
    }
}

