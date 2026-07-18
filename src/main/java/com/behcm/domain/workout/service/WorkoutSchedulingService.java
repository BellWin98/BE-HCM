package com.behcm.domain.workout.service;

import com.behcm.domain.penalty.service.PenaltyService;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkoutSchedulingService {

    private final WorkoutRoomMemberRepository workoutRoomMemberRepository;
    private final PenaltyService penaltyService;
    private final WorkoutRoomService workoutRoomService;

    @Scheduled(cron = "0 0 0 * * MON")
    @Transactional
    public void weeklyProcessing() {
        log.info("Starting weekly processing");

        // 벌금 계산 및 부과 (전환 반영 전, 지난주 설정 기준으로 실행)
        penaltyService.calculateAndAssignPenalties();

        // 예약된 벌금제도 전환 반영 (이번 주부터 적용)
        workoutRoomService.applyDuePendingPenaltyChanges();

        // 주간 운동 횟수 리셋
        resetWeeklyWorkouts();

        log.info("Weekly processing completed");
    }

    private void resetWeeklyWorkouts() {
        log.info("Starting weekly workout reset");

        workoutRoomMemberRepository.resetWeeklyWorkouts();

        log.info("Weekly workout reset completed");
    }
}