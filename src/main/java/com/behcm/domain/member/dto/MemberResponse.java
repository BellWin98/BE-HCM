package com.behcm.domain.member.dto;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MemberResponse {

    private Long id;
    private String email;
    private String nickname;
    private String profileUrl;
    private MemberRole role;
    private Integer totalWorkoutDays;
    private Long totalPenalty;
    private LocalDateTime createdAt;

    public static MemberResponse from(Member member) {
        return MemberResponse.builder()
                .id(member.getId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .role(member.getRole())
                .totalWorkoutDays(member.getTotalWorkoutDays())
                .totalPenalty(member.getTotalPenalty())
                .createdAt(member.getCreatedAt())
                .build();
    }
}
