package com.behcm.domain.social.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 운동 인증에 남길 수 있는 리액션 이모지.
 *
 * DB 에는 이름(MUSCLE 등)으로 저장하고, 실제 이모지 문자는 응답에만 실어 보낸다.
 * 이모지 문자를 그대로 저장하면 나중에 표기를 바꿀 때 기존 데이터를 전부 마이그레이션해야 한다.
 */
@Getter
@RequiredArgsConstructor
public enum ReactionEmoji {

    MUSCLE("💪"),
    FIRE("🔥"),
    CLAP("👏"),
    THUMBS_UP("👍"),
    PARTY("🎉");

    private final String symbol;
}
