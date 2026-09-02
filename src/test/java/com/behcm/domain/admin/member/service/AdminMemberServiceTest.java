package com.behcm.domain.admin.member.service;

import com.behcm.domain.admin.member.dto.AdminMemberResponse;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.chat.repository.ChatMessageRepository;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.domain.member.repository.MemberSettingsRepository;
import com.behcm.domain.notification.repository.FcmTokenRepository;
import com.behcm.domain.penalty.repository.PenaltyRepository;
import com.behcm.domain.rest.repository.RestRepository;
import com.behcm.domain.social.repository.WorkoutCommentRepository;
import com.behcm.domain.social.repository.WorkoutReactionRepository;
import com.behcm.domain.tossstock.repository.TossAccessRepository;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import com.behcm.domain.workout.repository.WorkoutRoomRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminMemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private TossAccessRepository tossAccessRepository;

    @Mock
    private WorkoutRoomRepository workoutRoomRepository;

    @Mock
    private WorkoutRoomMemberRepository workoutRoomMemberRepository;

    @Mock
    private WorkoutRecordRepository workoutRecordRepository;

    @Mock
    private WorkoutReactionRepository workoutReactionRepository;

    @Mock
    private WorkoutCommentRepository workoutCommentRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private FcmTokenRepository fcmTokenRepository;

    @Mock
    private MemberSettingsRepository memberSettingsRepository;

    @Mock
    private PenaltyRepository penaltyRepository;

    @Mock
    private RestRepository restRepository;

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

    @Test
    @DisplayName("getMembers는 toss_access 를 한 번에 조회해 회원별 토스 접근 여부를 채운다")
    void getMembers_fillsTossAccessFlag() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        Member granted = memberWithId(1L, "granted@example.com", "granted");
        Member denied = memberWithId(2L, "denied@example.com", "denied");

        given(memberRepository.searchAdminMembers(isNull(), isNull(), eq(pageable)))
                .willReturn(new PageImpl<>(List.of(granted, denied), pageable, 2));
        given(tossAccessRepository.findGrantedMemberIds(List.of(1L, 2L))).willReturn(Set.of(1L));

        // when
        Page<AdminMemberResponse> result = adminMemberService.getMembers(null, null, pageable);

        // then
        assertThat(result.getContent().get(0).isTossAccess()).isTrue();
        assertThat(result.getContent().get(1).isTossAccess()).isFalse();

        // 건별 조회를 추가하면 그대로 N+1 이 되므로, 페이지당 한 번만 조회해야 한다.
        verify(tossAccessRepository).findGrantedMemberIds(List.of(1L, 2L));
    }

    @Test
    @DisplayName("deleteMember는 회원을 지우기 전에 토스 접근 권한을 먼저 삭제한다")
    void deleteMember_deletesTossAccessBeforeMember() {
        // given
        Member admin = memberWithId(1L, "admin@example.com", "admin");
        Member target = memberWithId(2L, "target@example.com", "target");

        given(memberRepository.findById(2L)).willReturn(Optional.of(target));
        given(workoutRoomRepository.findByOwner(target)).willReturn(List.of());
        given(workoutRoomMemberRepository.findByMember(target)).willReturn(List.of());
        given(memberSettingsRepository.findByMemberId(2L)).willReturn(Optional.empty());

        // when
        adminMemberService.deleteMember(2L, admin);

        // then — toss_access 가 member 를 FK 로 참조하므로 순서가 뒤집히면 제약에 걸린다.
        InOrder inOrder = inOrder(tossAccessRepository, memberRepository);
        inOrder.verify(tossAccessRepository).deleteByMemberId(2L);
        inOrder.verify(memberRepository).delete(target);
    }

    private Member memberWithId(Long id, String email, String nickname) {
        Member member = Member.builder()
                .email(email)
                .password("encoded")
                .nickname(nickname)
                .role(MemberRole.USER)
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
