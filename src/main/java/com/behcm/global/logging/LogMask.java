package com.behcm.global.logging;

/**
 * 로그에 실을 수 없는 값을 "같은 값인지 구분은 되게" 가린다.
 *
 * <p>이메일은 개인정보이고, JWT·refresh·FCM 토큰은 자격증명이다. 원문이 로그 수집기에 남으면
 * 로그 접근 권한이 곧 계정 접근 권한이 된다. 반면 완전히 지우면 "같은 사용자가 10번 실패했다"를
 * 볼 수 없으므로 앞·뒤 일부만 남긴다.
 */
public final class LogMask {

    private LogMask() {
    }

    /** {@code ab***@example.com} — 로컬파트 앞 두 글자와 도메인만 남긴다. */
    public static String email(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + email.substring(at);
    }

    /** {@code ***abc123} — 끝 6자리만 남긴다. 토큰·입장코드·인증코드처럼 짧은 비밀값에 쓴다. */
    public static String secret(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= 6) {
            return "***";
        }
        return "***" + value.substring(value.length() - 6);
    }
}
