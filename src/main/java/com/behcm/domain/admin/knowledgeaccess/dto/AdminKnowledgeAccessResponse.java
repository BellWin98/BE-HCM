package com.behcm.domain.admin.knowledgeaccess.dto;

import com.behcm.domain.knowledge.entity.KnowledgeAccess;
import com.behcm.domain.member.entity.Member;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminKnowledgeAccessResponse {

    private Long memberId;
    private String email;
    private String nickname;
    private String profileUrl;
    private boolean canRead;
    private boolean canWrite;
    /** 변경한 관리자의 id. 행이 없는 경우(권한 없음, ADMIN 대상)에는 null 이다. */
    private Long grantedBy;
    private LocalDateTime updatedAt;

    public static AdminKnowledgeAccessResponse from(KnowledgeAccess access) {
        Member member = access.getMember();
        return AdminKnowledgeAccessResponse.builder()
                .memberId(member.getId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .profileUrl(member.getProfileUrl())
                .canRead(access.isCanRead())
                .canWrite(access.isCanWrite())
                .grantedBy(access.getGrantedBy())
                .updatedAt(access.getUpdatedAt())
                .build();
    }

    /** 저장된 행이 없는 회원(권한 없음, 또는 항상 허용되는 ADMIN)용 응답. */
    public static AdminKnowledgeAccessResponse of(Member member, boolean canRead, boolean canWrite) {
        return AdminKnowledgeAccessResponse.builder()
                .memberId(member.getId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .profileUrl(member.getProfileUrl())
                .canRead(canRead)
                .canWrite(canWrite)
                .build();
    }
}
