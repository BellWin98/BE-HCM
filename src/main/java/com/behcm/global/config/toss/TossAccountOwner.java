package com.behcm.global.config.toss;

import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Locale;

/**
 * 토스증권 계좌 소유자. 계좌 하나당 client_id/client_secret 한 쌍이 발급되므로
 * 소유자가 곧 인증 컨텍스트의 단위가 된다.
 */
@Getter
@RequiredArgsConstructor
public enum TossAccountOwner {

    ME("나"),
    MOM("엄마"),
    DAD("아빠");

    private final String displayName;

    /**
     * 요청 파라미터를 enum 으로 변환한다.
     * Spring 의 기본 enum 컨버터는 변환 실패 시 MethodArgumentTypeMismatchException 을 던지는데
     * GlobalExceptionHandler 가 이를 다루지 않아 500 이 나가므로, 직접 변환해 400 으로 떨어뜨린다.
     */
    public static TossAccountOwner from(String value) {
        if (value == null || value.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(owner -> owner.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT));
    }
}
