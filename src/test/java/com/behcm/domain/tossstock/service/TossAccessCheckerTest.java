package com.behcm.domain.tossstock.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.tossstock.repository.TossAccessRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TossAccessCheckerTest {

    @Mock
    private TossAccessRepository tossAccessRepository;

    @InjectMocks
    private TossAccessChecker tossAccessChecker;

    private Member member(Long id, MemberRole role) {
        Member member = Member.builder()
                .email("user@example.com")
                .password("encoded")
                .nickname("user")
                .role(role)
                .build();
        if (id != null) ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    @Test
    @DisplayName("ADMIN은 toss_access 에 등록되지 않아도 접근할 수 있다")
    void canAccess_withAdminRole_returnsTrueWithoutLookup() {
        assertThat(tossAccessChecker.canAccess(member(1L, MemberRole.ADMIN))).isTrue();

        // ADMIN 은 항상 통과하므로 조회 자체가 필요 없다.
        verify(tossAccessRepository, never()).existsByMemberId(1L);
    }

    @Test
    @DisplayName("toss_access 에 등록된 회원은 접근할 수 있다")
    void canAccess_withGrantedMember_returnsTrue() {
        given(tossAccessRepository.existsByMemberId(2L)).willReturn(true);

        assertThat(tossAccessChecker.canAccess(member(2L, MemberRole.USER))).isTrue();
    }

    @Test
    @DisplayName("등록되지 않은 USER는 접근할 수 없다")
    void canAccess_withUngrantedUser_returnsFalse() {
        given(tossAccessRepository.existsByMemberId(3L)).willReturn(false);

        assertThat(tossAccessChecker.canAccess(member(3L, MemberRole.USER))).isFalse();
    }

    @Test
    @DisplayName("FAMILY 역할만으로는 접근할 수 없다 — 토스 접근은 role 과 분리되어 있다")
    void canAccess_withFamilyRoleOnly_returnsFalse() {
        given(tossAccessRepository.existsByMemberId(4L)).willReturn(false);

        assertThat(tossAccessChecker.canAccess(member(4L, MemberRole.FAMILY))).isFalse();
    }

    @Test
    @DisplayName("Member 가 아닌 principal(익명 인증의 문자열)은 조회 없이 거부한다")
    void canAccess_withNonMemberPrincipal_returnsFalse() {
        assertThat(tossAccessChecker.canAccess("anonymousUser")).isFalse();
        assertThat(tossAccessChecker.canAccess(null)).isFalse();
    }

    @Test
    @DisplayName("id 가 없는 Member 는 조회하지 않고 거부한다")
    void canAccess_withTransientMember_returnsFalse() {
        assertThat(tossAccessChecker.canAccess(member(null, MemberRole.USER))).isFalse();

        verify(tossAccessRepository, never()).existsByMemberId(null);
    }
}
