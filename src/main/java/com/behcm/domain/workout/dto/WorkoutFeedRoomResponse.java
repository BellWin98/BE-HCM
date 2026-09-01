package com.behcm.domain.workout.dto;

import com.behcm.domain.social.dto.ReactionCountResponse;
import com.behcm.domain.social.dto.WorkoutSocialSummary;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 피드 한 장에 묶인 방별 내역. 같은 인증이 방마다 별도 레코드로 저장되므로
 * 리액션/댓글도 방 단위로 나뉜다.
 */
@Getter
@Builder
public class WorkoutFeedRoomResponse {

    private Long roomId;
    private String roomName;

    /** 이 방에 저장된 운동 인증 id. 리액션/댓글 API 는 이 id 로 호출해야 한다. */
    private Long recordId;

    /** 리액션이 하나라도 달린 이모지만 담긴다. 개수 내림차순. */
    private List<ReactionCountResponse> reactions;
    private long commentCount;

    public static WorkoutFeedRoomResponse of(Long roomId, String roomName, Long recordId, WorkoutSocialSummary social) {
        WorkoutSocialSummary summary = social != null ? social : WorkoutSocialSummary.empty();
        return WorkoutFeedRoomResponse.builder()
                .roomId(roomId)
                .roomName(roomName)
                .recordId(recordId)
                .reactions(summary.getReactions())
                .commentCount(summary.getCommentCount())
                .build();
    }
}
