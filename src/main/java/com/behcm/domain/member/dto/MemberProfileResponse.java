package com.behcm.domain.member.dto;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MemberProfileResponse {

    private Long id;
    private String nickname;
    private String email;
    private String profileUrl;
    private String bio;
    private Integer totalWorkoutDays;
    private Integer currentStreak;
    private Integer longestStreak;
    private Long totalPenalty;
    private LocalDateTime joinedAt;
    private MemberRole role;

    public static MemberProfileResponse from(Member member, Integer currentStreak, Integer longestStreak) {
        return MemberProfileResponse.builder()
                .id(member.getId())
                .nickname(member.getNickname())
                .email(member.getEmail())
                .profileUrl(member.getProfileUrl())
                .bio(member.getBio())
                .totalWorkoutDays(member.getTotalWorkoutDays())
                .currentStreak(currentStreak)
                .longestStreak(longestStreak)
                .totalPenalty(member.getTotalPenalty())
                .joinedAt(member.getCreatedAt())
                .role(member.getRole())
                .build();
    }
}
