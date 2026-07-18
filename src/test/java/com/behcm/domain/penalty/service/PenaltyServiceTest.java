package com.behcm.domain.penalty.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.penalty.entity.Penalty;
import com.behcm.domain.penalty.repository.PenaltyAccountRepository;
import com.behcm.domain.penalty.repository.PenaltyRepository;
import com.behcm.domain.rest.repository.RestRepository;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.domain.workout.repository.WorkoutRoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PenaltyServiceTest {

    @Mock
    private WorkoutRoomRepository workoutRoomRepository;

    @Mock
    private WorkoutRecordRepository workoutRecordRepository;

    @Mock
    private RestRepository restRepository;

    @Mock
    private PenaltyAccountRepository penaltyAccountRepository;

    @Mock
    private PenaltyRepository penaltyRepository;

    @InjectMocks
    private PenaltyService penaltyService;

    private Member member() {
        return Member.builder()
                .email("user@test.com")
                .nickname("user")
                .role(MemberRole.USER)
                .build();
    }

    private WorkoutRoom room(boolean penaltyEnabled, Long penaltyPerMiss) {
        Member owner = member();
        return WorkoutRoom.builder()
                .name("Test Room")
                .minWeeklyWorkouts(3)
                .penaltyEnabled(penaltyEnabled)
                .penaltyPerMiss(penaltyPerMiss)
                .maxMembers(10)
                .entryCode("ENTRY01")
                .owner(owner)
                .build();
    }

    @Test
    @DisplayName("벌금제도가 꺼진 방은 멤버가 목표를 못 채워도 벌금을 생성하지 않는다 (penaltyPerMiss가 null이어도 NPE 없음)")
    void calculateAndAssignPenalties_skipsPenaltyDisabledRoom() {
        WorkoutRoom disabledRoom = room(false, null);
        WorkoutRoomMember member = WorkoutRoomMember.builder().member(member()).workoutRoom(disabledRoom).build();
        disabledRoom.getWorkoutRoomMembers().add(member);

        given(workoutRoomRepository.findByIsActiveTrue()).willReturn(List.of(disabledRoom));

        penaltyService.calculateAndAssignPenalties();

        // penaltyEnabled=false 가드가 먼저 걸려 운동 기록/휴식 조회 자체가 일어나지 않아야 한다.
        verify(workoutRecordRepository, never()).countByWorkoutRoomAndWorkoutDateBetweenGroupByMember(any(), any(), any());
        verify(penaltyRepository, never()).save(any(Penalty.class));
    }

    @Test
    @DisplayName("벌금제도가 켜진 방에서 목표를 못 채운 멤버에게는 penaltyPerMiss 기준으로 벌금이 부과된다")
    void calculateAndAssignPenalties_assignsPenaltyForEnabledRoom() {
        WorkoutRoom enabledRoom = room(true, 5000L);
        WorkoutRoomMember member = WorkoutRoomMember.builder().member(member()).workoutRoom(enabledRoom).build();
        enabledRoom.getWorkoutRoomMembers().add(member);

        given(workoutRoomRepository.findByIsActiveTrue()).willReturn(List.of(enabledRoom));
        // 주 3회 목표인데 실제로는 기록이 없어(빈 리스트) 3회 모두 미달 처리됨
        given(workoutRecordRepository.countByWorkoutRoomAndWorkoutDateBetweenGroupByMember(any(), any(), any()))
                .willReturn(List.of());
        given(restRepository.findAllByWorkoutRoomMemberIn(any())).willReturn(List.of());

        penaltyService.calculateAndAssignPenalties();

        ArgumentCaptor<Penalty> penaltyCaptor = ArgumentCaptor.forClass(Penalty.class);
        verify(penaltyRepository).save(penaltyCaptor.capture());
        assertThat(penaltyCaptor.getValue().getPenaltyAmount()).isEqualTo(3 * 5000L);
        assertThat(member.getTotalPenalty()).isEqualTo(3 * 5000L);
    }
}
