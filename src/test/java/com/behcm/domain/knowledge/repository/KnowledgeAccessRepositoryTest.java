package com.behcm.domain.knowledge.repository;

import com.behcm.domain.knowledge.entity.KnowledgeAccess;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class KnowledgeAccessRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private KnowledgeAccessRepository repository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private EntityManager em;

    private Member saveMember(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        return memberRepository.save(Member.builder()
                .email(prefix + "-" + suffix + "@test.com")
                .password("pw")
                .nickname(prefix + suffix.substring(0, 4))
                .build());
    }

    @Test
    @DisplayName("upsert 는 처음에는 삽입하고 이후에는 같은 행을 갱신한다")
    void upsert_insertsThenUpdatesSingleRow() {
        Member member = saveMember("m");

        repository.upsert(member.getId(), true, false, 9L);
        repository.upsert(member.getId(), false, true, 9L);
        em.clear();

        KnowledgeAccess access = repository.findByMemberId(member.getId()).orElseThrow();
        assertThat(access.isCanRead()).isFalse();
        assertThat(access.isCanWrite()).isTrue();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("findByMemberIdIn 은 권한이 부여된 회원의 행만 반환한다")
    void findByMemberIdIn_returnsOnlyGranted() {
        Member a = saveMember("a");
        Member b = saveMember("b");
        Member c = saveMember("c");

        repository.upsert(b.getId(), true, true, 9L);

        List<KnowledgeAccess> result = repository.findByMemberIdIn(List.of(a.getId(), b.getId(), c.getId()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMember().getId()).isEqualTo(b.getId());
    }
}
