package com.behcm.domain.admin.knowledgeaccess.service;

import com.behcm.domain.admin.knowledgeaccess.dto.AdminKnowledgeAccessRequest;
import com.behcm.domain.admin.knowledgeaccess.dto.AdminKnowledgeAccessResponse;
import com.behcm.domain.knowledge.entity.KnowledgeAccess;
import com.behcm.domain.knowledge.repository.KnowledgeAccessRepository;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import com.behcm.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 컨텍스트의 AdminKnowledgeAccessService 는 IntegrationTestSupport 의 목이므로,
 * 실제 레포지토리를 주입해 서비스를 직접 만들어 실 DB 로 검증한다.
 */
@Transactional
class AdminKnowledgeAccessServiceTest extends IntegrationTestSupport {

    @Autowired
    private KnowledgeAccessRepository knowledgeAccessRepository;
    @Autowired
    private MemberRepository memberRepository;

    private AdminKnowledgeAccessService service;

    @BeforeEach
    void setUp() {
        service = new AdminKnowledgeAccessService(knowledgeAccessRepository, memberRepository);
    }

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
    @DisplayName("update 는 읽기 권한을 부여하면 행을 만든다")
    void update_grantRead_createsRow() {
        Member admin = saveMember("admin", MemberRole.ADMIN);
        Member target = saveMember("t", MemberRole.USER);

        AdminKnowledgeAccessResponse response =
                service.update(target.getId(), new AdminKnowledgeAccessRequest(true, false), admin);

        assertThat(response.getMemberId()).isEqualTo(target.getId());
        assertThat(response.isCanRead()).isTrue();
        assertThat(response.isCanWrite()).isFalse();
        assertThat(response.getGrantedBy()).isEqualTo(admin.getId());
        KnowledgeAccess row = knowledgeAccessRepository.findByMemberId(target.getId()).orElseThrow();
        assertThat(row.isCanRead()).isTrue();
        assertThat(row.isCanWrite()).isFalse();
    }

    @Test
    @DisplayName("update 는 두 권한이 모두 false 이면 행을 삭제한다")
    void update_bothFalse_deletesRow() {
        Member admin = saveMember("admin", MemberRole.ADMIN);
        Member target = saveMember("t", MemberRole.USER);
        service.update(target.getId(), new AdminKnowledgeAccessRequest(true, true), admin);

        AdminKnowledgeAccessResponse response =
                service.update(target.getId(), new AdminKnowledgeAccessRequest(false, false), admin);

        assertThat(response.isCanRead()).isFalse();
        assertThat(response.isCanWrite()).isFalse();
        assertThat(knowledgeAccessRepository.findByMemberId(target.getId())).isEmpty();
    }

    @Test
    @DisplayName("update 는 신규 회원에게 다른 값으로 두 번 호출해도 행이 하나이고 마지막 값이다")
    void update_twiceForNewMember_keepsSingleRow() {
        Member admin = saveMember("admin", MemberRole.ADMIN);
        Member target = saveMember("t", MemberRole.USER);

        service.update(target.getId(), new AdminKnowledgeAccessRequest(true, false), admin);
        service.update(target.getId(), new AdminKnowledgeAccessRequest(false, true), admin);

        assertThat(knowledgeAccessRepository.findByMemberIdIn(java.util.List.of(target.getId()))).hasSize(1);
        KnowledgeAccess row = knowledgeAccessRepository.findByMemberId(target.getId()).orElseThrow();
        assertThat(row.isCanRead()).isFalse();
        assertThat(row.isCanWrite()).isTrue();
    }

    @Test
    @DisplayName("update 는 ADMIN 대상이면 저장하지 않고 모두 true 로 응답한다")
    void update_adminTarget_noRowAndAllTrue() {
        Member admin = saveMember("admin", MemberRole.ADMIN);
        Member otherAdmin = saveMember("admin2", MemberRole.ADMIN);

        AdminKnowledgeAccessResponse response =
                service.update(otherAdmin.getId(), new AdminKnowledgeAccessRequest(false, false), admin);

        assertThat(response.isCanRead()).isTrue();
        assertThat(response.isCanWrite()).isTrue();
        assertThat(knowledgeAccessRepository.findByMemberId(otherAdmin.getId())).isEmpty();
    }

    @Test
    @DisplayName("update 는 존재하지 않는 회원이면 MEMBER_NOT_FOUND 를 던진다")
    void update_unknownMember_throwsMemberNotFound() {
        Member admin = saveMember("admin", MemberRole.ADMIN);

        assertThatThrownBy(() -> service.update(Long.MAX_VALUE, new AdminKnowledgeAccessRequest(true, true), admin))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }
}
