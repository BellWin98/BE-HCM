package com.behcm.domain.workout.dto;

import com.behcm.domain.workout.entity.WorkoutRecord;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class WorkoutRecordResponse {

    private Long id;
    private LocalDate workoutDate;
    private List<String> workoutTypes;
    private Integer duration;
    private List<String> imageUrls;
    private LocalDateTime createdAt;

    public static WorkoutRecordResponse from(WorkoutRecord workoutRecord) {
        return WorkoutRecordResponse.builder()
                .id(workoutRecord.getId())
                .workoutDate(workoutRecord.getWorkoutDate())
                .workoutTypes(workoutRecord.getWorkoutTypes())
                .duration(workoutRecord.getDuration())
                .imageUrls(workoutRecord.getImageUrls())
                .createdAt(workoutRecord.getCreatedAt())
                .build();
    }
}
