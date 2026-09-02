package com.behcm.domain.admin.member.dto;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminMemberResponse {

    private Long id;
    private String email;
    private String nickname;
    private String profileUrl;
    private MemberRole role;
    private Integer totalWorkoutDays;
    private Long totalPenalty;
    private LocalDateTime createdAt;
    /** 토스증권 접근 권한 보유 여부. role 과 별개로 toss_access 에 등록되어 있는지를 뜻한다. */
    private boolean tossAccess;

    public static AdminMemberResponse from(Member member, boolean tossAccess) {
        return AdminMemberResponse.builder()
                .id(member.getId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .profileUrl(member.getProfileUrl())
                .role(member.getRole())
                .totalWorkoutDays(member.getTotalWorkoutDays())
                .totalPenalty(member.getTotalPenalty())
                .createdAt(member.getCreatedAt())
                .tossAccess(tossAccess)
                .build();
    }
}

