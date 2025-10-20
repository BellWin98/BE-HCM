package com.behcm.global.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class PerformanceInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTRIBUTE = "startTime";
    private static final long SLOW_REQUEST_THRESHOLD_MS = 1000; // 1초

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTRIBUTE);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;

            String method = request.getMethod();
            String uri = request.getRequestURI();
            String queryString = request.getQueryString();
            String fullUrl = queryString != null ? uri + "?" + queryString : uri;

            if (duration > SLOW_REQUEST_THRESHOLD_MS) {
                log.warn("SLOW REQUEST: {} {} took {} ms", method, fullUrl, duration);
            } else {
                log.info("REQUEST: {} {} took {} ms", method, fullUrl, duration);
            }

            // 응답 코드별 로깅
            int status = response.getStatus();
            if (status >= 400) {
                log.warn("ERROR RESPONSE: {} {} returned status {} in {} ms", method, fullUrl, status, duration);
            }
        }
    }
}