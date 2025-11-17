package com.behcm.domain.workout.dto;

import com.behcm.domain.workout.entity.WorkoutRecord;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class WorkoutFeedItemResponse {

    private Long id;
    private LocalDate workoutDate;
    private List<String> workoutTypes;
    private Integer duration;
    private List<String> imageUrls;
    private Long likes;
    private Long comments;
    private Boolean isLiked;
    private LocalDateTime createdAt;
    private String roomName;

    public static WorkoutFeedItemResponse from(WorkoutRecord workoutRecord, Long likes, Long comments, Boolean isLiked) {
        return WorkoutFeedItemResponse.builder()
                .id(workoutRecord.getId())
                .workoutDate(workoutRecord.getWorkoutDate())
                .workoutTypes(workoutRecord.getWorkoutTypes())
                .duration(workoutRecord.getDuration())
                .imageUrls(workoutRecord.getImageUrls())
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
                .workoutTypes(workoutRecord.getWorkoutTypes())
                .duration(workoutRecord.getDuration())
                .imageUrls(workoutRecord.getImageUrls())
                .createdAt(workoutRecord.getCreatedAt())
                .roomName(workoutRecord.getWorkoutRoom().getName())
                .build();
    }
}
