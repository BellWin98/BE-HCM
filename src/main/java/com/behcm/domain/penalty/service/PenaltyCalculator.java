package com.behcm.domain.penalty.service;

import com.behcm.domain.penalty.entity.Penalty;
import com.behcm.domain.rest.entity.Rest;
import com.behcm.domain.rest.repository.RestRepository;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

import static com.behcm.global.util.DateUtils.isDateInRange;

@Component
@RequiredArgsConstructor
@Slf4j
public class PenaltyCalculator {

    private final WorkoutRecordRepository workoutRecordRepository;
    private final RestRepository restRepository;

    public Penalty calculatePenalty(WorkoutRoomMember member, WorkoutRoom workoutRoom,
                                   LocalDate weekStart, LocalDate weekEnd) {
        if (isMemberOnBreak(member, weekStart, weekEnd)) {
            log.debug("Skipping penalty calculation for member {} - on break", member.getNickname());
            return null;
        }

        int actualWorkouts = (int) workoutRecordRepository.countByMemberAndWorkoutRoomAndWorkoutDateBetween(
                member.getMember(), workoutRoom, weekStart, weekEnd);
        int requiredWorkouts = workoutRoom.getMinWeeklyWorkouts();

        if (actualWorkouts >= requiredWorkouts) {
            log.debug("No penalty for {} in room {} - met requirements (Required: {}, Actual: {})",
                    member.getNickname(), workoutRoom.getName(), requiredWorkouts, actualWorkouts);
            return null;
        }

        int missedWorkouts = requiredWorkouts - actualWorkouts;
        long penaltyAmount = missedWorkouts * workoutRoom.getPenaltyPerMiss();

        log.info("Penalty assigned to {} in room {}: {}원 (Required: {}, Actual: {})",
                member.getNickname(), workoutRoom.getName(), penaltyAmount, requiredWorkouts, actualWorkouts);

        return Penalty.builder()
                .workoutRoomMember(member)
                .penaltyAmount(penaltyAmount)
                .requiredWorkouts(requiredWorkouts)
                .actualWorkouts(actualWorkouts)
                .weekStartDate(weekStart)
                .weekEndDate(weekEnd)
                .build();
    }

    private boolean isMemberOnBreak(WorkoutRoomMember member, LocalDate weekStart, LocalDate weekEnd) {
        List<Rest> restPeriods = restRepository.findAllByWorkoutRoomMember(member);

        return restPeriods.stream().anyMatch(rest ->
                isDateInRange(weekStart, rest.getStartDate(), rest.getEndDate()) ||
                isDateInRange(weekEnd, rest.getStartDate(), rest.getEndDate()) ||
                (rest.getStartDate().isBefore(weekStart) && rest.getEndDate().isAfter(weekEnd))
        );
    }
}