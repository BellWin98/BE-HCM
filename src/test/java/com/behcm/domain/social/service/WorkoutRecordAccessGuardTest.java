package com.behcm.domain.social.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class WorkoutRecordAccessGuardTest {

    @Mock
    private WorkoutRecordRepository workoutRecordRepository;

    @Mock
    private WorkoutRoomMemberRepository workoutRoomMemberRepository;

    @InjectMocks
    private WorkoutRecordAccessGuard workoutRecordAccessGuard;

    private Member member() {
        return Member.builder()
                .email("user@test.com")
                .nickname("user")
                .role(MemberRole.USER)
                .build();
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
        return WorkoutRecord.builder()
                .member(owner)
                .workoutRoom(room)
                .workoutDate(LocalDate.now())
                .duration(30)
                .build();
    }

    @Test
    @DisplayName("getAccessibleRecord는 같은 운동방 멤버에게 운동 기록을 반환한다")
    void getAccessibleRecord_roomMember_returnsRecord() {
        Member viewer = member();
        WorkoutRecord record = record(viewer);
        given(workoutRecordRepository.findById(1L)).willReturn(Optional.of(record));
        given(workoutRoomMemberRepository.existsByMemberAndWorkoutRoom(viewer, record.getWorkoutRoom())).willReturn(true);

        WorkoutRecord result = workoutRecordAccessGuard.getAccessibleRecord(viewer, 1L);

        assertThat(result).isSameAs(record);
    }

    @Test
    @DisplayName("getAccessibleRecord는 존재하지 않는 운동 기록이면 예외를 던진다")
    void getAccessibleRecord_missingRecord_throws() {
        given(workoutRecordRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> workoutRecordAccessGuard.getAccessibleRecord(member(), 1L))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.WORKOUT_RECORD_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("getAccessibleRecord는 운동방 멤버가 아니면 예외를 던진다")
    void getAccessibleRecord_notRoomMember_throws() {
        Member owner = member();
        Member outsider = Member.builder().email("out@test.com").nickname("out").role(MemberRole.USER).build();
        WorkoutRecord record = record(owner);
        given(workoutRecordRepository.findById(1L)).willReturn(Optional.of(record));
        given(workoutRoomMemberRepository.existsByMemberAndWorkoutRoom(outsider, record.getWorkoutRoom())).willReturn(false);

        assertThatThrownBy(() -> workoutRecordAccessGuard.getAccessibleRecord(outsider, 1L))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.NOT_WORKOUT_ROOM_MEMBER.getMessage());
    }
}
