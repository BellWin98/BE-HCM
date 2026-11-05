package com.behcm.domain.knowledge.repository;

/** 목록 필터. MINE = 내 메모, PUBLIC = 공개 메모, PRIVATE = 비공개 메모. 여러 개를 고르면 OR 로 합친다. */
public enum KnowledgeNoteScope {
    MINE, PUBLIC, PRIVATE
}
