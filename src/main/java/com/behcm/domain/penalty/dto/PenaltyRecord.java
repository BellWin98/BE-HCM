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
    private Long penaltyAmount;
    private Integer requiredWorkouts;
    private Integer actualWorkouts;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private Boolean isPaid;
    private LocalDateTime paidAt;

    public static PenaltyRecord from(Penalty penalty) {
        return PenaltyRecord.builder()
                .id(penalty.getId())
                .penaltyAmount(penalty.getPenaltyAmount())
                .requiredWorkouts(penalty.getRequiredWorkouts())
                .actualWorkouts(penalty.getActualWorkouts())
                .weekStartDate(penalty.getWeekStartDate())
                .weekEndDate(penalty.getWeekEndDate())
                .isPaid(penalty.getIsPaid())
                .paidAt(penalty.getPaidAt())
                .build();
    }
}