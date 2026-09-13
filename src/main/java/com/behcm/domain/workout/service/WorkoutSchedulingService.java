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

    /**
     * 세 단계가 한 트랜잭션이다 — 어느 단계든 실패하면 그 주의 벌금·전환·리셋이 통째로 빠진다.
     * 프레임워크도 ERROR 를 남기지만 "몇 단계까지 갔는지"는 여기서만 알 수 있다.
     */
    @Scheduled(cron = "0 0 0 * * MON")
    @Transactional
    public void weeklyProcessing() {
        log.info("Weekly processing started");
        long startNanos = System.nanoTime();
        String step = "penalty calculation";
        try {
            // 벌금 계산 및 부과 (전환 반영 전, 지난주 설정 기준으로 실행)
            penaltyService.calculateAndAssignPenalties();

            // 예약된 벌금제도 전환 반영 (이번 주부터 적용)
            step = "pending penalty change";
            workoutRoomService.applyDuePendingPenaltyChanges();

            // 주간 운동 횟수 리셋
            step = "weekly workout reset";
            resetWeeklyWorkouts();

            log.info("Weekly processing completed ({}ms)", elapsedMs(startNanos));
        } catch (RuntimeException e) {
            log.error("Weekly processing failed at step '{}' after {}ms; the whole transaction is rolled back",
                    step, elapsedMs(startNanos), e);
            throw e;
        }
    }

    private void resetWeeklyWorkouts() {
        int reset = workoutRoomMemberRepository.resetWeeklyWorkouts();
        log.info("Weekly workout counts reset (members={})", reset);
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
