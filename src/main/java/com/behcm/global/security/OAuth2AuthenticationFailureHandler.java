package com.behcm.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@Slf4j
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        // 사용자가 동의 화면에서 취소해도 여기로 온다. 서버 잘못이 아니므로 WARN — 특정 provider 에서만
        // 급증하면 그쪽 설정(redirect URI, 시크릿 만료)을 의심한다.
        log.warn("OAuth2 login failed: {} - {}", request.getRequestURI(), exception.getMessage());

        String redirectUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/login")
                .queryParam("error", "oauth_failed")
                .queryParam("message", "소셜 로그인에 실패했습니다.")
                .build()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
