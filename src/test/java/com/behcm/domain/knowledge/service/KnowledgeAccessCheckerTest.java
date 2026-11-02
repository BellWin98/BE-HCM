package com.behcm.domain.knowledge.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.tossstock.entity.TossAccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeAccessCheckerTest {

    private final KnowledgeAccessChecker knowledgeAccessChecker = new KnowledgeAccessChecker();

    private Member member(MemberRole role) {
        Member member = Member.builder()
                .email("user@example.com")
                .password("encoded")
                .nickname("user")
                .role(role)
                .build();
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    @Test
    @DisplayName("ADMIN은 접근할 수 있다")
    void canAccess_admin_true() {
        assertThat(knowledgeAccessChecker.canAccess(member(MemberRole.ADMIN))).isTrue();
    }

    @Test
    @DisplayName("USER와 FAMILY는 접근할 수 없다")
    void canAccess_userOrFamily_false() {
        assertThat(knowledgeAccessChecker.canAccess(member(MemberRole.USER))).isFalse();
        assertThat(knowledgeAccessChecker.canAccess(member(MemberRole.FAMILY))).isFalse();
    }

    @Test
    @DisplayName("Member 가 아닌 principal(익명 인증의 문자열, null)은 예외 없이 거부한다")
    void canAccess_anonymousString_false() {
        assertThat(knowledgeAccessChecker.canAccess("anonymousUser")).isFalse();
        assertThat(knowledgeAccessChecker.canAccess(null)).isFalse();
    }

    @Test
    @DisplayName("토스 접근 권한(toss_access)이 있는 USER와 FAMILY도 메모에는 접근할 수 없다")
    void canAccess_tossAccessUserOrFamily_false() {
        Member tossUser = member(MemberRole.USER);
        Member family = member(MemberRole.FAMILY);
        TossAccess tossUserAccess = TossAccess.builder().member(tossUser).grantedBy(99L).build();
        TossAccess familyAccess = TossAccess.builder().member(family).grantedBy(99L).build();

        assertThat(tossUserAccess.getMember()).isSameAs(tossUser);
        assertThat(familyAccess.getMember()).isSameAs(family);
        assertThat(knowledgeAccessChecker.canAccess(tossUser)).isFalse();
        assertThat(knowledgeAccessChecker.canAccess(family)).isFalse();
    }
}
