package com.behcm.domain.knowledge.service;

import com.behcm.domain.knowledge.entity.KnowledgeAccess;
import com.behcm.domain.knowledge.repository.KnowledgeAccessRepository;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.tossstock.entity.TossAccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KnowledgeAccessCheckerTest {

    private final KnowledgeAccessRepository repository = mock(KnowledgeAccessRepository.class);
    private final KnowledgeAccessChecker knowledgeAccessChecker = new KnowledgeAccessChecker(repository);

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

    private void givenRow(Member member, boolean canRead, boolean canWrite) {
        when(repository.findByMemberId(member.getId())).thenReturn(Optional.of(
                KnowledgeAccess.builder().member(member).canRead(canRead).canWrite(canWrite).grantedBy(99L).build()));
    }

    @Test
    @DisplayName("ADMIN은 접근할 수 있다")
    void canAccess_admin_true() {
        assertThat(knowledgeAccessChecker.canAccess(member(MemberRole.ADMIN))).isTrue();
        assertThat(knowledgeAccessChecker.canWrite(member(MemberRole.ADMIN))).isTrue();
    }

    @Test
    @DisplayName("ADMIN은 권한 행을 조회하지 않고 ADMIN 권한을 받는다")
    void permissionsOf_admin_returnsAdminWithoutQuery() {
        KnowledgePermissions p = knowledgeAccessChecker.permissionsOf(member(MemberRole.ADMIN));

        assertThat(p).isEqualTo(KnowledgePermissions.ADMIN);
        assertThat(p.admin()).isTrue();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("USER와 FAMILY는 권한 행의 플래그를 그대로 받는다")
    void permissionsOf_userWithRow_returnsFlags() {
        for (MemberRole role : new MemberRole[]{MemberRole.USER, MemberRole.FAMILY}) {
            Member m = member(role);
            givenRow(m, true, false);

            KnowledgePermissions p = knowledgeAccessChecker.permissionsOf(m);

            assertThat(p.admin()).isFalse();
            assertThat(p.canRead()).isTrue();
            assertThat(p.canWrite()).isFalse();
            assertThat(knowledgeAccessChecker.canAccess(m)).isTrue();
            assertThat(knowledgeAccessChecker.canWrite(m)).isFalse();
        }
    }

    @Test
    @DisplayName("권한 행이 없는 일반 회원은 NONE")
    void permissionsOf_userWithoutRow_returnsNone() {
        when(repository.findByMemberId(any())).thenReturn(Optional.empty());

        assertThat(knowledgeAccessChecker.permissionsOf(member(MemberRole.USER))).isEqualTo(KnowledgePermissions.NONE);
        assertThat(knowledgeAccessChecker.canAccess(member(MemberRole.USER))).isFalse();
        assertThat(knowledgeAccessChecker.canAccess(member(MemberRole.FAMILY))).isFalse();
    }

    @Test
    @DisplayName("id 가 없는 Member 는 NONE")
    void permissionsOf_memberWithoutId_returnsNone() {
        Member noId = Member.builder().email("x@example.com").password("p").nickname("x")
                .role(MemberRole.USER).build();

        assertThat(knowledgeAccessChecker.permissionsOf(noId)).isEqualTo(KnowledgePermissions.NONE);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Member 가 아닌 principal(익명 인증의 문자열, null)은 예외 없이 거부한다")
    void canAccess_anonymousString_false() {
        assertThat(knowledgeAccessChecker.canAccess("anonymousUser")).isFalse();
        assertThat(knowledgeAccessChecker.canAccess(null)).isFalse();
        assertThat(knowledgeAccessChecker.canWrite("anonymousUser")).isFalse();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("토스 접근 권한(toss_access)만 있는 USER와 FAMILY는 메모에 접근할 수 없다")
    void canAccess_tossAccessUserOrFamily_false() {
        when(repository.findByMemberId(any())).thenReturn(Optional.empty());
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
