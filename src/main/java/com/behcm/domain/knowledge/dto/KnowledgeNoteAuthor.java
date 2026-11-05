package com.behcm.domain.knowledge.dto;

import com.behcm.domain.member.entity.Member;

/** 메모 작성자 요약. 다른 회원의 공개 메모를 볼 때 누가 썼는지 보여 주기 위한 최소 정보만 담는다. */
public record KnowledgeNoteAuthor(Long id, String nickname, String profileUrl) {

    public static KnowledgeNoteAuthor from(Member member) {
        return new KnowledgeNoteAuthor(member.getId(), member.getNickname(), member.getProfileUrl());
    }
}
