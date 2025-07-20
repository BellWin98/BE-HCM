package com.behcm.domain.workout.service;

import com.behcm.domain.workout.entity.WorkoutRoomMember;
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

    @Scheduled(cron = "0 0 0 * * MON")
    @Transactional
    public void resetWeeklyWorkouts() {
        log.info("Starting weekly workout reset");
        
        workoutRoomMemberRepository.findAll().forEach(WorkoutRoomMember::resetWeeklyWorkouts);
        
        log.info("Weekly workout reset completed");
    }
}