package com.behcm.domain.admin.tossaccess.dto;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.tossstock.entity.TossAccess;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminTossAccessResponse {

    private Long memberId;
    private String email;
    private String nickname;
    private String profileUrl;
    /** 부여한 관리자의 id. 마이그레이션으로 승계된 행은 null 이다. */
    private Long grantedBy;
    private LocalDateTime grantedAt;

    public static AdminTossAccessResponse from(TossAccess tossAccess) {
        Member member = tossAccess.getMember();
        return AdminTossAccessResponse.builder()
                .memberId(member.getId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .profileUrl(member.getProfileUrl())
                .grantedBy(tossAccess.getGrantedBy())
                .grantedAt(tossAccess.getCreatedAt())
                .build();
    }
}
