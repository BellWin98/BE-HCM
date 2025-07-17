package com.behcm.domain.workout.dto;

import com.behcm.domain.workout.entity.WorkoutRecord;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class WorkoutResponse {
    private Long id;
    private LocalDate workoutDate;
    private String workoutType;
    private Integer duration;
    private String imageUrl;

    public static WorkoutResponse from(WorkoutRecord workoutRecord) {
        return WorkoutResponse.builder()
                .id(workoutRecord.getId())
                .workoutDate(workoutRecord.getWorkoutDate())
                .workoutType(workoutRecord.getWorkoutType())
                .duration(workoutRecord.getDuration())
                .imageUrl(workoutRecord.getImageUrl())
                .build();
    }
}