package com.behcm.global.security;

import com.behcm.domain.member.entity.Member;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 로그 패턴의 {@code %X{memberId}} 가 읽는 MDC 키. */
    public static final String MDC_MEMBER_ID = "memberId";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response, final FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);
            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                String email = jwtTokenProvider.getEmailFromJwt(jwt);
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                // 이 요청의 나머지 로그가 행위자를 갖도록 한다. 제거는 RequestLoggingFilter 가 요청 끝에 한다.
                if (userDetails instanceof Member member && member.getId() != null) {
                    MDC.put(MDC_MEMBER_ID, String.valueOf(member.getId()));
                }
            }
        } catch (UsernameNotFoundException ex) {
            // 토큰은 유효한데 회원이 없다 — 관리자가 삭제한 계정의 남은 세션이다. 인증 없이 통과시켜 401 로 떨어뜨린다.
            log.warn("Valid JWT for a member that no longer exists: {} {} - {}",
                    request.getMethod(), request.getRequestURI(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context: {} {}",
                    request.getMethod(), request.getRequestURI(), ex);
        }
        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")){
            return bearerToken.substring(7);
        }

        return null;
    }
}
