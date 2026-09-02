package com.behcm.domain.social.dto;

import com.behcm.domain.social.entity.ReactionEmoji;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReactionCountResponse {

    /** 이모지 식별자(MUSCLE 등). 리액션 등록 요청에 그대로 실어 보내면 된다. */
    private String emoji;

    /** 화면에 그릴 이모지 문자(💪 등). */
    private String symbol;

    private long count;

    /** 조회 주체가 이 이모지를 눌렀는지 여부. */
    private boolean reactedByMe;

    public static ReactionCountResponse of(ReactionEmoji emoji, long count, boolean reactedByMe) {
        return ReactionCountResponse.builder()
                .emoji(emoji.name())
                .symbol(emoji.getSymbol())
                .count(count)
                .reactedByMe(reactedByMe)
                .build();
    }
}
