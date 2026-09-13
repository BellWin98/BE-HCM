package com.behcm.global.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(final HttpServletRequest request, final HttpServletResponse response, final AuthenticationException authException) throws IOException, ServletException {
        // 인증 없이 보호 경로를 친 것은 예외 상황이 아니다(토큰 만료 후 재시도, 로그아웃 상태의 탭 등).
        // 토큰 자체의 거부 사유는 JwtTokenProvider/JwtAuthenticationFilter 가 이미 남긴다.
        log.debug("Unauthenticated request rejected: {} {} - {}",
                request.getMethod(), request.getRequestURI(), authException.getMessage());

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        body.put("error", "Unauthorized");
        body.put("message", authException.getMessage());
        body.put("path", request.getServletPath());

        JsonMapper mapper = new JsonMapper();
        mapper.writeValue(response.getOutputStream(), body);
    }
}
