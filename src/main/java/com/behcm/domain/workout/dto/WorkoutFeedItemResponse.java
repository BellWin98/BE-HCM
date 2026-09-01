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
public class WorkoutFeedItemResponse {

    private Long id;
    private LocalDate workoutDate;
    private List<String> workoutTypes;
    private Integer duration;
    private List<String> imageUrls;
    private LocalDateTime createdAt;

    /** 대표 인증이 저장된 방 이름. 방별 내역은 {@link #rooms} 를 쓴다. */
    private String roomName;

    /**
     * 이 인증이 저장된 방 전체와 방별 리액션/댓글.
     *
     * 운동 인증 한 번이 참여 중인 방 수만큼 레코드로 저장되므로, 피드는 하루 한 장으로 묶어 보여주되
     * 리액션/댓글은 어느 방 것인지 구분할 수 있어야 한다. 리액션/댓글 API 는 여기 담긴 recordId 로 호출한다.
     */
    private List<WorkoutFeedRoomResponse> rooms;

    /** 방 전체를 합친 집계. 리액션이 하나라도 달린 이모지만 담기며 개수 내림차순. */
    private List<ReactionCountResponse> reactions;

    /** 방 전체를 합친 댓글 수. */
    private long commentCount;

    public static WorkoutFeedItemResponse of(WorkoutRecord workoutRecord, WorkoutSocialSummary social) {
        return of(workoutRecord, social, List.of());
    }

    public static WorkoutFeedItemResponse of(WorkoutRecord workoutRecord,
                                             WorkoutSocialSummary social,
                                             List<WorkoutFeedRoomResponse> rooms) {
        WorkoutSocialSummary summary = social != null ? social : WorkoutSocialSummary.empty();
        return WorkoutFeedItemResponse.builder()
                .id(workoutRecord.getId())
                .workoutDate(workoutRecord.getWorkoutDate())
                .workoutTypes(workoutRecord.getWorkoutTypes())
                .duration(workoutRecord.getDuration())
                .imageUrls(workoutRecord.getImageUrls())
                .createdAt(workoutRecord.getCreatedAt())
                .roomName(workoutRecord.getWorkoutRoom().getName())
                .reactions(summary.getReactions())
                .commentCount(summary.getCommentCount())
                .rooms(rooms != null ? rooms : List.of())
                .build();
    }

    public static WorkoutFeedItemResponse from(WorkoutRecord workoutRecord) {
        return of(workoutRecord, WorkoutSocialSummary.empty());
    }
}
