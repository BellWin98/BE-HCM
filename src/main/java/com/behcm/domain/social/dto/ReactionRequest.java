package com.behcm.domain.social.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReactionRequest {

    /**
     * ReactionEmoji 의 이름(MUSCLE, FIRE, CLAP, THUMBS_UP, PARTY).
     *
     * enum 으로 직접 받으면 알 수 없는 값이 왔을 때 HttpMessageNotReadableException 이 되어
     * 공통 핸들러의 catch-all 에 걸려 500 이 나간다. 문자열로 받아 서비스에서 400 으로 변환한다.
     */
    @NotBlank(message = "리액션 이모지는 필수입니다.")
    private String emoji;
}
