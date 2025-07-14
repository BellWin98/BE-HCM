package com.behcm.domain.member.dto;

import com.behcm.domain.member.entity.Member;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MemberResponse {

    private Long id;
    private String email;
    private String nickname;
    private Integer totalWorkoutDays;
    private Double achievementRate;
    private Long totalPenalty;
    private LocalDateTime createdAt;

    public static MemberResponse from(Member member) {
        return MemberResponse.builder()
                .id(member.getId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .totalWorkoutDays(member.getTotalWorkoutDays())
                .achievementRate(member.getAchievementRate())
                .totalPenalty(member.getTotalPenalty())
                .createdAt(member.getCreatedAt())
                .build();
    }
}
