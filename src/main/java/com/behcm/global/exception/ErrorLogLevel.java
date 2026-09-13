package com.behcm.global.exception;

import org.slf4j.event.Level;
import org.springframework.http.HttpStatus;

import java.util.EnumSet;
import java.util.Set;

/**
 * {@link ErrorCode} 를 어느 로그 레벨로 남길지 정한다.
 *
 * <p>기준은 "누가 고쳐야 하는가"다.
 * <ul>
 *   <li><b>ERROR</b> — 우리가 고쳐야 한다. 500 뿐이다(설정 누락, 정합성 붕괴, 외부 인증 실패).</li>
 *   <li><b>WARN</b> — 외부가 고치거나(502/503/429) 빈도를 지켜봐야 한다(401/403, 무차별 대입·IDOR 징후).</li>
 *   <li><b>INFO</b> — 사용자가 고친다. 입력 오류·비즈니스 규칙 위반(400/404/409/422)은 정상 흐름이다.</li>
 * </ul>
 *
 * <p>레벨은 대부분 HTTP 상태코드에서 파생되고, 보안상 의미가 있는 4xx 만 {@link #SECURITY_SENSITIVE} 로
 * WARN 으로 승격한다. 예외 하나하나에 레벨을 박아 두면 새 코드를 추가할 때마다 빠뜨리기 쉽다.
 */
public final class ErrorLogLevel {

    /**
     * 4xx 지만 반복되면 공격 징후로 봐야 하는 코드. 짧은 코드 공간을 무차별 대입하거나(인증코드·입장코드),
     * 남의 리소스를 건드리려 한 경우(IDOR)다.
     */
    private static final Set<ErrorCode> SECURITY_SENSITIVE = EnumSet.of(
            ErrorCode.INVALID_CREDENTIALS,
            ErrorCode.INVALID_VERIFICATION_CODE,
            ErrorCode.INVALID_ENTRY_CODE,
            ErrorCode.NOT_COMMENT_AUTHOR
    );

    private ErrorLogLevel() {
    }

    public static Level of(ErrorCode errorCode) {
        HttpStatus status = errorCode.getHttpStatus();
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            return Level.ERROR;
        }
        if (status.is5xxServerError()
                || status == HttpStatus.TOO_MANY_REQUESTS
                || status == HttpStatus.UNAUTHORIZED
                || status == HttpStatus.FORBIDDEN
                || SECURITY_SENSITIVE.contains(errorCode)) {
            return Level.WARN;
        }
        return Level.INFO;
    }
}
