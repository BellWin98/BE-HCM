package com.behcm.domain.social.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.social.dto.ReactionRequest;
import com.behcm.domain.social.entity.ReactionEmoji;
import com.behcm.domain.social.entity.WorkoutReaction;
import com.behcm.domain.social.repository.WorkoutReactionRepository;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkoutReactionServiceTest {

    @Mock
    private WorkoutReactionRepository workoutReactionRepository;

    @Mock
    private WorkoutRecordAccessGuard workoutRecordAccessGuard;

    @Mock
    private WorkoutSocialQueryService workoutSocialQueryService;

    @InjectMocks
    private WorkoutReactionService workoutReactionService;

    private Member member(long id, String nickname) {
        Member member = Member.builder()
                .email(nickname + "@test.com")
                .nickname(nickname)
                .role(MemberRole.USER)
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private WorkoutRecord record(Member owner) {
        WorkoutRoom room = WorkoutRoom.builder()
                .name("Test Room")
                .minWeeklyWorkouts(3)
                .penaltyEnabled(false)
                .maxMembers(10)
                .entryCode("ENTRY01")
                .owner(owner)
                .build();
        WorkoutRecord record = WorkoutRecord.builder()
                .member(owner)
                .workoutRoom(room)
                .workoutDate(LocalDate.now())
                .duration(30)
                .build();
        ReflectionTestUtils.setField(record, "id", 10L);
        return record;
    }

    private ReactionRequest request(String emoji) {
        ReactionRequest request = new ReactionRequest();
        request.setEmoji(emoji);
        return request;
    }

    @Test
    @DisplayName("react는 기존 리액션이 없으면 새로 저장한다")
    void react_firstTime_savesReaction() {
        Member reactor = member(2L, "reactor");
        WorkoutRecord record = record(member(1L, "owner"));
        given(workoutRecordAccessGuard.getAccessibleRecord(reactor, 10L)).willReturn(record);
        given(workoutReactionRepository.findByWorkoutRecordAndMember(record, reactor)).willReturn(Optional.empty());
        given(workoutSocialQueryService.summarize(anyCollection(), any(Member.class))).willReturn(Map.of());

        workoutReactionService.react(reactor, 10L, request("MUSCLE"));

        ArgumentCaptor<WorkoutReaction> captor = ArgumentCaptor.forClass(WorkoutReaction.class);
        verify(workoutReactionRepository).save(captor.capture());
        assertThat(captor.getValue().getEmoji()).isEqualTo(ReactionEmoji.MUSCLE);
        assertThat(captor.getValue().getMember()).isEqualTo(reactor);
        assertThat(captor.getValue().getWorkoutRecord()).isEqualTo(record);
    }

    @Test
    @DisplayName("react는 이미 리액션이 있으면 새로 저장하지 않고 이모지만 바꾼다")
    void react_existingReaction_changesEmojiInPlace() {
        Member reactor = member(2L, "reactor");
        WorkoutRecord record = record(member(1L, "owner"));
        WorkoutReaction existing = WorkoutReaction.builder()
                .workoutRecord(record)
                .member(reactor)
                .emoji(ReactionEmoji.MUSCLE)
                .build();
        given(workoutRecordAccessGuard.getAccessibleRecord(reactor, 10L)).willReturn(record);
        given(workoutReactionRepository.findByWorkoutRecordAndMember(record, reactor)).willReturn(Optional.of(existing));
        given(workoutSocialQueryService.summarize(anyCollection(), any(Member.class))).willReturn(Map.of());

        workoutReactionService.react(reactor, 10L, request("FIRE"));

        assertThat(existing.getEmoji()).isEqualTo(ReactionEmoji.FIRE);
        verify(workoutReactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("react는 지원하지 않는 이모지면 예외를 던진다")
    void react_unsupportedEmoji_throws() {
        Member reactor = member(2L, "reactor");

        assertThatThrownBy(() -> workoutReactionService.react(reactor, 10L, request("ROCKET")))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.UNSUPPORTED_REACTION.getMessage());

        verify(workoutReactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelReaction은 내가 누른 리액션을 삭제한다")
    void cancelReaction_deletesExisting() {
        Member reactor = member(2L, "reactor");
        WorkoutRecord record = record(member(1L, "owner"));
        WorkoutReaction existing = WorkoutReaction.builder()
                .workoutRecord(record)
                .member(reactor)
                .emoji(ReactionEmoji.MUSCLE)
                .build();
        given(workoutRecordAccessGuard.getAccessibleRecord(reactor, 10L)).willReturn(record);
        given(workoutReactionRepository.findByWorkoutRecordAndMember(record, reactor)).willReturn(Optional.of(existing));
        given(workoutSocialQueryService.summarize(anyCollection(), any(Member.class))).willReturn(Map.of());

        workoutReactionService.cancelReaction(reactor, 10L);

        verify(workoutReactionRepository).delete(existing);
    }

    @Test
    @DisplayName("cancelReaction은 누른 리액션이 없으면 예외를 던진다")
    void cancelReaction_withoutReaction_throws() {
        Member reactor = member(2L, "reactor");
        WorkoutRecord record = record(member(1L, "owner"));
        given(workoutRecordAccessGuard.getAccessibleRecord(reactor, 10L)).willReturn(record);
        given(workoutReactionRepository.findByWorkoutRecordAndMember(record, reactor)).willReturn(Optional.empty());

        assertThatThrownBy(() -> workoutReactionService.cancelReaction(reactor, 10L))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.REACTION_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("getReactionMembers는 운동방 멤버만 조회할 수 있다")
    void getReactionMembers_delegatesToAccessGuard() {
        Member viewer = member(2L, "viewer");
        given(workoutRecordAccessGuard.getAccessibleRecord(viewer, 10L))
                .willThrow(new CustomException(ErrorCode.NOT_WORKOUT_ROOM_MEMBER));

        assertThatThrownBy(() -> workoutReactionService.getReactionMembers(viewer, 10L))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.NOT_WORKOUT_ROOM_MEMBER.getMessage());

        verify(workoutReactionRepository, never()).findAllByWorkoutRecordFetchMember(any());
    }

    @Test
    @DisplayName("getReactionMembers는 리액션을 누른 회원 목록을 반환한다")
    void getReactionMembers_returnsReactors() {
        Member viewer = member(2L, "viewer");
        WorkoutRecord record = record(member(1L, "owner"));
        WorkoutReaction reaction = WorkoutReaction.builder()
                .workoutRecord(record)
                .member(viewer)
                .emoji(ReactionEmoji.CLAP)
                .build();
        given(workoutRecordAccessGuard.getAccessibleRecord(viewer, 10L)).willReturn(record);
        given(workoutReactionRepository.findAllByWorkoutRecordFetchMember(record)).willReturn(List.of(reaction));

        assertThat(workoutReactionService.getReactionMembers(viewer, 10L))
                .singleElement()
                .satisfies(response -> {
                    assertThat(response.getNickname()).isEqualTo("viewer");
                    assertThat(response.getEmoji()).isEqualTo("CLAP");
                    assertThat(response.getSymbol()).isEqualTo(ReactionEmoji.CLAP.getSymbol());
                });
    }
}
