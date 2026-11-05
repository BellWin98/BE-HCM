package com.behcm.domain.knowledge.entity;

/** 지식 메모 공개 범위. PUBLIC 은 조회 권한자 모두가, PRIVATE 은 작성자와 관리자만 볼 수 있다. */
public enum KnowledgeVisibility {
    PUBLIC,
    PRIVATE
}
