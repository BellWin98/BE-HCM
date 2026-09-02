package com.behcm.domain.social.dto;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.social.entity.WorkoutReaction;
import lombok.Builder;
import lombok.Getter;

/**
 * "누가 무슨 리액션을 눌렀는지" 목록의 한 줄.
 */
@Getter
@Builder
public class ReactionMemberResponse {

    private Long memberId;
    private String nickname;
    private String profileUrl;
    private String emoji;
    private String symbol;

    public static ReactionMemberResponse from(WorkoutReaction reaction) {
        Member member = reaction.getMember();
        return ReactionMemberResponse.builder()
                .memberId(member.getId())
                .nickname(member.getNickname())
                .profileUrl(member.getProfileUrl())
                .emoji(reaction.getEmoji().name())
                .symbol(reaction.getEmoji().getSymbol())
                .build();
    }
}
