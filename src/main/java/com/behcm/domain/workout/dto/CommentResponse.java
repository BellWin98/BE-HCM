package com.behcm.domain.workout.dto;

import com.behcm.domain.workout.entity.WorkoutComment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CommentResponse {

    private Long id;
    private Long workoutId;
    private Long memberId;
    private String nickname;
    private String profileUrl;
    private String content;
    private LocalDateTime createdAt;

    public static CommentResponse from(WorkoutComment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .workoutId(comment.getWorkoutRecord().getId())
                .memberId(comment.getMember().getId())
                .nickname(comment.getMember().getNickname())
                .profileUrl(comment.getMember().getProfileUrl())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
