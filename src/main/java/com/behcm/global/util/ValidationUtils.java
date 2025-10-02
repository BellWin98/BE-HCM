package com.behcm.global.util;

import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;

import java.time.LocalDate;

public final class ValidationUtils {

    private ValidationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isBefore(LocalDate.now())) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "시작 날짜는 오늘 이후여야 합니다.");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "종료 날짜는 시작 날짜보다 뒤여야 합니다.");
        }
    }

    public static void validateNotNull(Object object, ErrorCode errorCode) {
        if (object == null) {
            throw new CustomException(errorCode);
        }
    }

    public static void validateNotNull(Object object, ErrorCode errorCode, String message) {
        if (object == null) {
            throw new CustomException(errorCode, message);
        }
    }

    public static void validateCondition(boolean condition, ErrorCode errorCode) {
        if (!condition) {
            throw new CustomException(errorCode);
        }
    }

    public static void validateCondition(boolean condition, ErrorCode errorCode, String message) {
        if (!condition) {
            throw new CustomException(errorCode, message);
        }
    }
}