package com.behcm.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 요청 하나의 로그를 한 줄로 꿰는 필터.
 *
 * <p>요청마다 {@code requestId} 를 만들어 MDC 에 넣고 응답 헤더로도 돌려준다. 로그 패턴이
 * {@code %X{requestId} %X{memberId}} 를 찍으므로, 이 필터 뒤에서 남는 모든 로그는 "어느 요청에서,
 * 누가"를 자동으로 갖는다. {@code memberId} 는 인증이 끝난 뒤 {@code JwtAuthenticationFilter} 가 넣는다.
 *
 * <p>MDC 는 스레드에 붙어 있고 톰캣은 스레드를 재사용하므로, 요청이 끝나면 반드시 비운다 —
 * 안 비우면 다음 요청의 로그가 이전 사용자의 memberId 를 달고 나간다.
 *
 * <p>순서는 Security 필터 체인({@code -100})보다 앞이어야 인증 로그에도 requestId 가 붙는다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String MDC_REQUEST_ID = "requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** 외부 API 를 여러 번 묶어 부르는 엔드포인트(토스 orderable, KIS 손익)가 이 값을 넘기면 원인을 봐야 한다. */
    static final long DEFAULT_SLOW_REQUEST_THRESHOLD_MS = 3_000L;

    /** 클라이언트가 보낸 id 는 로그에 그대로 찍히므로 개행·제어문자를 막는다(로그 위조 방지). */
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private static final String HEALTH_CHECK_PATH = "/api/health";

    private final long slowRequestThresholdMs;

    public RequestLoggingFilter() {
        this(DEFAULT_SLOW_REQUEST_THRESHOLD_MS);
    }

    RequestLoggingFilter(long slowRequestThresholdMs) {
        this.slowRequestThresholdMs = slowRequestThresholdMs;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HEALTH_CHECK_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            if (elapsedMs >= slowRequestThresholdMs) {
                log.warn("Slow request: {} {} -> {} ({}ms)",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMs);
            } else {
                log.debug("{} {} -> {} ({}ms)",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMs);
            }
            // requestId 뿐 아니라 인증 필터가 넣은 memberId 까지 전부 비운다.
            MDC.clear();
        }
    }

    private static String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        if (incoming != null && SAFE_REQUEST_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
