package com.behcm.domain.penalty.dto;

import com.behcm.domain.penalty.entity.Penalty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class PenaltyRecord {
    private Long id;
    private Long workoutRoomMemberId;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private Integer requiredWorkouts;
    private Integer actualWorkouts;
    private Long penaltyAmount;
    private Boolean isPaid;
    private LocalDateTime paidAt;

    public static PenaltyRecord from(Penalty penalty) {
        return PenaltyRecord.builder()
                .id(penalty.getId())
                .workoutRoomMemberId(penalty.getWorkoutRoomMember().getId())
                .weekStartDate(penalty.getWeekStartDate())
                .weekEndDate(penalty.getWeekEndDate())
                .requiredWorkouts(penalty.getRequiredWorkouts())
                .actualWorkouts(penalty.getActualWorkouts())
                .penaltyAmount(penalty.getPenaltyAmount())
                .isPaid(penalty.getIsPaid())
                .paidAt(penalty.getPaidAt())
                .build();
    }
}