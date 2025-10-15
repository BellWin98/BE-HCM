package com.behcm.domain.workout.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WorkoutStatsResponse {

    private Integer totalWorkouts;
    private Integer currentStreak;
    private Integer longestStreak;
    private Integer weeklyGoal;
    private Integer weeklyProgress;
    private Integer monthlyWorkouts;
    private String favoriteWorkoutType;
    private Integer totalDuration; // 총 운동 시간 (분)
}
