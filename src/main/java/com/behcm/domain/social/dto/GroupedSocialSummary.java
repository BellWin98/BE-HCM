package com.behcm.domain.social.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * 같은 운동 인증이 방마다 한 건씩 저장되는 구조(운동방 N개 → WorkoutRecord N건) 때문에 필요한 집계.
 *
 * 마이페이지 피드는 하루 한 장만 그리므로 형제 인증의 반응을 모두 합친 {@link #total} 을 쓰고,
 * 방별 내역이 필요한 화면은 {@link #byRecordId} 를 쓴다.
 */
@Getter
@Builder
public class GroupedSocialSummary {

    /** 그룹 전체 합계. 한 회원이 여러 방에서 같은 인증에 리액션해도 이모지당 1명으로 센다. */
    private WorkoutSocialSummary total;

    /** 그룹에 속한 인증별 집계. key = workout_record id. 요청한 id 는 모두 키로 존재한다. */
    private Map<Long, WorkoutSocialSummary> byRecordId;

    private static final GroupedSocialSummary EMPTY = GroupedSocialSummary.builder()
            .total(WorkoutSocialSummary.empty())
            .byRecordId(Map.of())
            .build();

    public static GroupedSocialSummary empty() {
        return EMPTY;
    }
}
