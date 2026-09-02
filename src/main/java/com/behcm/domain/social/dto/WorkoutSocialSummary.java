package com.behcm.domain.social.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 인증 한 건의 리액션/댓글 집계. 인증을 담아 내려보내는 응답들(피드, 운동방 상세)에 함께 실린다.
 */
@Getter
@Builder
public class WorkoutSocialSummary {

    /** 리액션이 하나라도 달린 이모지만 담긴다. 개수 내림차순. */
    private List<ReactionCountResponse> reactions;

    private long commentCount;

    private static final WorkoutSocialSummary EMPTY = WorkoutSocialSummary.builder()
            .reactions(List.of())
            .commentCount(0L)
            .build();

    public static WorkoutSocialSummary empty() {
        return EMPTY;
    }
}
