package com.behcm.domain.admin.tossaccess.service;

import com.behcm.domain.admin.tossaccess.dto.AdminTossAccessResponse;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.domain.tossstock.entity.TossAccess;
import com.behcm.domain.tossstock.repository.TossAccessRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminTossAccessServiceTest {

    @Mock
    private TossAccessRepository tossAccessRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private AdminTossAccessService adminTossAccessService;

    private Member member(Long id, String nickname) {
        Member member = Member.builder()
                .email(nickname + "@example.com")
                .password("encoded")
                .nickname(nickname)
                .role(MemberRole.USER)
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    @Test
    @DisplayName("grant는 권한이 없던 회원에게 toss_access 행을 만든다")
    void grant_createsAccessRow() {
        Member admin = member(1L, "admin");
        Member target = member(2L, "target");

        given(memberRepository.findById(2L)).willReturn(Optional.of(target));
        given(tossAccessRepository.findByMemberId(2L)).willReturn(Optional.empty());
        given(tossAccessRepository.save(any(TossAccess.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        AdminTossAccessResponse response = adminTossAccessService.grant(2L, admin);

        assertThat(response.getMemberId()).isEqualTo(2L);
        assertThat(response.getNickname()).isEqualTo("target");
        assertThat(response.getGrantedBy()).isEqualTo(1L);
    }

    @Test
    @DisplayName("grant는 이미 권한이 있는 회원이면 새 행을 만들지 않는다")
    void grant_isIdempotent() {
        Member admin = member(1L, "admin");
        Member target = member(2L, "target");
        TossAccess existing = TossAccess.builder().member(target).grantedBy(9L).build();

        given(memberRepository.findById(2L)).willReturn(Optional.of(target));
        given(tossAccessRepository.findByMemberId(2L)).willReturn(Optional.of(existing));

        AdminTossAccessResponse response = adminTossAccessService.grant(2L, admin);

        assertThat(response.getMemberId()).isEqualTo(2L);
        assertThat(response.getGrantedBy()).isEqualTo(9L);
        verify(tossAccessRepository, never()).save(any(TossAccess.class));
    }

    @Test
    @DisplayName("grant는 존재하지 않는 회원에 대해 MEMBER_NOT_FOUND 를 던진다")
    void grant_whenMemberNotFound_throws() {
        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminTossAccessService.grant(999L, member(1L, "admin")))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("revoke는 권한이 없던 회원이어도 예외 없이 처리한다")
    void revoke_isIdempotent() {
        given(memberRepository.existsById(2L)).willReturn(true);

        adminTossAccessService.revoke(2L);

        verify(tossAccessRepository).deleteByMemberId(2L);
    }

    @Test
    @DisplayName("revoke는 존재하지 않는 회원에 대해 MEMBER_NOT_FOUND 를 던진다")
    void revoke_whenMemberNotFound_throws() {
        given(memberRepository.existsById(999L)).willReturn(false);

        assertThatThrownBy(() -> adminTossAccessService.revoke(999L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("getGrantedMembers는 권한이 부여된 회원 목록을 반환한다")
    void getGrantedMembers_returnsList() {
        Member target = member(2L, "target");
        given(tossAccessRepository.findAllWithMember())
                .willReturn(List.of(TossAccess.builder().member(target).grantedBy(1L).build()));

        List<AdminTossAccessResponse> result = adminTossAccessService.getGrantedMembers();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getEmail()).isEqualTo("target@example.com");
    }
}
