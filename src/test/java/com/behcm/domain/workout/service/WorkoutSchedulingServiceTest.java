package com.behcm.domain.workout.service;

import com.behcm.domain.penalty.service.PenaltyService;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkoutSchedulingServiceTest {

    @Mock
    private WorkoutRoomMemberRepository workoutRoomMemberRepository;

    @Mock
    private PenaltyService penaltyService;

    @Mock
    private WorkoutRoomService workoutRoomService;

    @InjectMocks
    private WorkoutSchedulingService workoutSchedulingService;

    @Test
    @DisplayName("weeklyProcessing은 벌금 계산 → 벌금제도 전환 반영 → 주간 운동횟수 리셋 순으로 실행된다")
    void weeklyProcessing_runsStepsInOrder() {
        workoutSchedulingService.weeklyProcessing();

        InOrder inOrder = inOrder(penaltyService, workoutRoomService, workoutRoomMemberRepository);
        inOrder.verify(penaltyService).calculateAndAssignPenalties();
        inOrder.verify(workoutRoomService).applyDuePendingPenaltyChanges();
        inOrder.verify(workoutRoomMemberRepository).resetWeeklyWorkouts();
    }
}
