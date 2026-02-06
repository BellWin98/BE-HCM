package com.behcm.domain.admin.member.service;

import com.behcm.domain.admin.member.dto.AdminMemberResponse;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminMemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private AdminMemberService adminMemberService;

    @Test
    @DisplayName("getMembers는 레포지토리에서 조회한 Member 페이지를 AdminMemberResponse 페이지로 매핑한다")
    void getMembers_mapsToAdminMemberResponsePage() {
        // given
        Pageable pageable = PageRequest.of(0, 10);

        Member member = Member.builder()
                .email("test@example.com")
                .password("encoded")
                .nickname("tester")
                .profileUrl("profile.png")
                .role(MemberRole.USER)
                .build();

        Page<Member> memberPage = new PageImpl<>(List.of(member), pageable, 1);
        given(memberRepository.searchAdminMembers(eq("query"), eq(MemberRole.USER), eq(pageable)))
                .willReturn(memberPage);

        // when
        Page<AdminMemberResponse> result =
                adminMemberService.getMembers("query", MemberRole.USER, pageable);

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
        AdminMemberResponse response = result.getContent().getFirst();
        assertThat(response.getEmail()).isEqualTo(member.getEmail());
        assertThat(response.getNickname()).isEqualTo(member.getNickname());
        assertThat(response.getRole()).isEqualTo(member.getRole());

        verify(memberRepository).searchAdminMembers("query", MemberRole.USER, pageable);
    }

    @Test
    @DisplayName("getMembers 호출 시 공백 query는 null로 정규화되어 레포지토리에 전달된다")
    void getMembers_normalizesBlankQueryToNull() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Member> emptyPage = Page.empty(pageable);
        given(memberRepository.searchAdminMembers(isNull(), eq(MemberRole.ADMIN), eq(pageable)))
                .willReturn(emptyPage);

        // when
        Page<AdminMemberResponse> result =
                adminMemberService.getMembers("   ", MemberRole.ADMIN, pageable);

        // then
        assertThat(result.getTotalElements()).isZero();
        verify(memberRepository).searchAdminMembers(null, MemberRole.ADMIN, pageable);
    }

    @Test
    @DisplayName("updateMemberRole은 회원의 역할을 변경하고 저장된 결과를 반환한다")
    void updateMemberRole_updatesRoleAndReturnsResponse() {
        // given
        Member member = Member.builder()
                .email("user@example.com")
                .password("encoded")
                .nickname("user")
                .profileUrl(null)
                .role(MemberRole.USER)
                .build();

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(memberRepository.save(member)).willReturn(member);

        // when
        AdminMemberResponse result =
                adminMemberService.updateMemberRole(1L, MemberRole.ADMIN);

        // then
        assertThat(member.getRole()).isEqualTo(MemberRole.ADMIN);
        assertThat(result.getRole()).isEqualTo(MemberRole.ADMIN);

        verify(memberRepository).findById(1L);
        verify(memberRepository).save(member);
    }

    @Test
    @DisplayName("updateMemberRole은 존재하지 않는 memberId에 대해 CustomException(MEMBER_NOT_FOUND)을 던진다")
    void updateMemberRole_whenMemberNotFound_throwsCustomException() {
        // given
        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminMemberService.updateMemberRole(999L, MemberRole.ADMIN))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }
}

