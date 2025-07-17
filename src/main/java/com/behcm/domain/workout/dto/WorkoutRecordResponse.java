package com.behcm.domain.workout.dto;

import com.behcm.domain.workout.entity.WorkoutRecord;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class WorkoutRecordResponse {

    private Long id;
    private LocalDate workoutDate;
    private String workoutType;
    private Integer duration;
    private String imageUrl;
    private LocalDateTime createdAt;

    public static WorkoutRecordResponse from(WorkoutRecord workoutRecord) {
        return WorkoutRecordResponse.builder()
                .id(workoutRecord.getId())
                .workoutDate(workoutRecord.getWorkoutDate())
                .workoutType(workoutRecord.getWorkoutType())
                .duration(workoutRecord.getDuration())
                .imageUrl(workoutRecord.getImageUrl())
                .createdAt(workoutRecord.getCreatedAt())
                .build();
    }
}
