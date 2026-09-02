package com.behcm.domain.workout.dto;

import com.behcm.domain.social.dto.ReactionCountResponse;
import com.behcm.domain.social.dto.WorkoutSocialSummary;
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

    /** 리액션이 하나라도 달린 이모지만 담긴다. 개수 내림차순. */
    private List<ReactionCountResponse> reactions;
    private long commentCount;

    public static WorkoutRecordResponse of(WorkoutRecord workoutRecord, WorkoutSocialSummary social) {
        WorkoutSocialSummary summary = social != null ? social : WorkoutSocialSummary.empty();
        return WorkoutRecordResponse.builder()
                .id(workoutRecord.getId())
                .workoutDate(workoutRecord.getWorkoutDate())
                .workoutTypes(workoutRecord.getWorkoutTypes())
                .duration(workoutRecord.getDuration())
                .imageUrls(workoutRecord.getImageUrls())
                .createdAt(workoutRecord.getCreatedAt())
                .reactions(summary.getReactions())
                .commentCount(summary.getCommentCount())
                .build();
    }

    /** 리액션/댓글 정보가 필요 없는 경로(관리자 조회 등)용. */
    public static WorkoutRecordResponse from(WorkoutRecord workoutRecord) {
        return of(workoutRecord, WorkoutSocialSummary.empty());
    }
}
