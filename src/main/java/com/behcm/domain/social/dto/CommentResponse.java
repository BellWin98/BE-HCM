package com.behcm.domain.social.dto;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.social.entity.WorkoutComment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CommentResponse {

    private Long id;
    private Long memberId;
    private String nickname;
    private String profileUrl;
    private String content;
    private LocalDateTime createdAt;

    /** 조회 주체가 쓴 댓글인지 여부. 삭제 버튼 노출 판단용. */
    private boolean mine;

    public static CommentResponse of(WorkoutComment comment, Member viewer) {
        Member author = comment.getMember();
        return CommentResponse.builder()
                .id(comment.getId())
                .memberId(author.getId())
                .nickname(author.getNickname())
                .profileUrl(author.getProfileUrl())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .mine(comment.isWrittenBy(viewer))
                .build();
    }
}
