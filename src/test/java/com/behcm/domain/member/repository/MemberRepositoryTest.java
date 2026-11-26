package com.behcm.domain.member.repository;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class MemberRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    private Member saveMember(String prefix, MemberRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        return memberRepository.save(Member.builder()
                .email(prefix + "-" + suffix + "@test.com")
                .password("pw")
                .nickname(prefix + suffix.substring(0, 4))
                .role(role)
                .build());
    }

    @Test
    @DisplayName("findIdsByRole 은 해당 역할 회원의 id 만 반환한다")
    void findIdsByRole_returnsAdminIds() {
        Member admin = saveMember("adm", MemberRole.ADMIN);
        Member user = saveMember("usr", MemberRole.USER);

        List<Long> result = memberRepository.findIdsByRole(MemberRole.ADMIN);

        assertThat(result).contains(admin.getId());
        assertThat(result).doesNotContain(user.getId());
    }
}
