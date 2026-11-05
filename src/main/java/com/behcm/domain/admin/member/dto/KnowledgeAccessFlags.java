package com.behcm.domain.admin.member.dto;

/** 회원 목록에 내려주는 지식 메모 권한. knowledge_access 행이 없는 회원은 {@link #NONE} 이다. */
public record KnowledgeAccessFlags(boolean canRead, boolean canWrite) {

    public static final KnowledgeAccessFlags NONE = new KnowledgeAccessFlags(false, false);
}
