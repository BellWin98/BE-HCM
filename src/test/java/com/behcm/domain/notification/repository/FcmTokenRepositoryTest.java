package com.behcm.domain.notification.repository;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.domain.notification.entity.FcmToken;
import com.behcm.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class FcmTokenRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private FcmTokenRepository repository;
    @Autowired
    private MemberRepository memberRepository;

    private Member saveMember(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        return memberRepository.save(Member.builder()
                .email(prefix + "-" + suffix + "@test.com")
                .password("pw")
                .nickname(prefix + suffix.substring(0, 4))
                .build());
    }

    private String saveToken(Member member) {
        String token = "token-" + UUID.randomUUID();
        repository.save(FcmToken.builder().member(member).token(token).build());
        return token;
    }

    @Test
    @DisplayName("findTokensByMemberIds 는 주어진 회원들의 토큰만 반환한다")
    void findTokensByMemberIds_returnsTokensOfGivenMembers() {
        Member a = saveMember("a");
        Member b = saveMember("b");
        Member other = saveMember("o");
        String tokenA = saveToken(a);
        String tokenB = saveToken(b);
        String tokenOther = saveToken(other);

        List<String> result = repository.findTokensByMemberIds(List.of(a.getId(), b.getId()));

        assertThat(result).containsExactlyInAnyOrder(tokenA, tokenB);
        assertThat(result).doesNotContain(tokenOther);
    }
}
