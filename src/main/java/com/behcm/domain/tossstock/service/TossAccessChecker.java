package com.behcm.domain.tossstock.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.tossstock.repository.TossAccessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 토스증권 접근 판정. {@code TossStockController} 의 {@code @PreAuthorize} SpEL 에서 참조한다.
 *
 * <p>인가는 이 한 곳에서만 판정한다 — {@code SecurityConfig} 의 URL 규칙에 같은 조건을 중복하면
 * 판정 로직이 두 군데로 갈라진다. 권한은 요청마다 DB 에서 다시 읽히므로
 * ({@code JwtAuthenticationFilter} → {@code UserDetailsServiceImpl}) 부여/회수가 토큰 재발급 없이 즉시 반영된다.
 */
@Component("tossAccessChecker")
@RequiredArgsConstructor
public class TossAccessChecker {

    private final TossAccessRepository tossAccessRepository;

    /**
     * principal 을 {@code Member} 가 아니라 {@code Object} 로 받는 이유: 익명 인증의 principal 은
     * {@code String("anonymousUser")} 라서, 타입을 좁히면 SpEL 이 변환에 실패해 403 대신 500 이 나간다.
     */
    @Transactional(readOnly = true)
    public boolean canAccess(Object principal) {
        if (!(principal instanceof Member member)) return false;
        if (member.getRole() == MemberRole.ADMIN) return true;
        if (member.getId() == null) return false;
        return tossAccessRepository.existsByMemberId(member.getId());
    }
}
