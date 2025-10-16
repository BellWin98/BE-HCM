package com.behcm.domain.workout.dto;

import com.behcm.domain.workout.entity.WorkoutRecord;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class WorkoutFeedItemResponse {

    private Long id;
    private LocalDate workoutDate;
    private String workoutType;
    private Integer duration;
    private String imageUrl;
    private Long likes;
    private Long comments;
    private Boolean isLiked;
    private LocalDateTime createdAt;
    private String roomName;

    public static WorkoutFeedItemResponse from(WorkoutRecord workoutRecord, Long likes, Long comments, Boolean isLiked) {
        return WorkoutFeedItemResponse.builder()
                .id(workoutRecord.getId())
                .workoutDate(workoutRecord.getWorkoutDate())
                .workoutType(workoutRecord.getWorkoutType())
                .duration(workoutRecord.getDuration())
                .imageUrl(workoutRecord.getImageUrl())
                .likes(likes)
                .comments(comments)
                .isLiked(isLiked)
                .createdAt(workoutRecord.getCreatedAt())
                .roomName(workoutRecord.getWorkoutRoom().getName())
                .build();
    }

    public static WorkoutFeedItemResponse from(WorkoutRecord workoutRecord) {
        return WorkoutFeedItemResponse.builder()
                .id(workoutRecord.getId())
                .workoutDate(workoutRecord.getWorkoutDate())
                .workoutType(workoutRecord.getWorkoutType())
                .duration(workoutRecord.getDuration())
                .imageUrl(workoutRecord.getImageUrl())
                .createdAt(workoutRecord.getCreatedAt())
                .roomName(workoutRecord.getWorkoutRoom().getName())
                .build();
    }
}
