package com.behcm.domain.workout.dto;

import lombok.AllArgsConstructor;
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