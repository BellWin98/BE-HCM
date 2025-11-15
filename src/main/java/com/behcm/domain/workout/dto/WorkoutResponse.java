package com.behcm.domain.workout.dto;

import com.behcm.domain.workout.entity.WorkoutRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public class WorkoutResponse {
    private LocalDate workoutDate;
    private List<String> workoutTypes;
    private Integer duration;
    private List<String> imageUrls;
}