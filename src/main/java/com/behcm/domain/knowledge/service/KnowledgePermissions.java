package com.behcm.domain.knowledge.service;

import com.behcm.domain.knowledge.entity.KnowledgeNote;

/**
 * 요청자의 지식 메모 권한. {@link KnowledgeAccessChecker#permissionsOf(Object)} 가 만든다.
 *
 * @param admin    ADMIN 여부. ADMIN 은 읽기·쓰기를 모두 가진다.
 * @param canRead  본인 메모 + 다른 회원의 공개 메모 조회
 * @param canWrite 본인 이름으로 생성, 본인 메모만 수정·삭제
 */
public record KnowledgePermissions(boolean admin, boolean canRead, boolean canWrite) {

    public static final KnowledgePermissions NONE = new KnowledgePermissions(false, false, false);
    public static final KnowledgePermissions ADMIN = new KnowledgePermissions(true, true, true);

    /** 지식 메모 메뉴·API 에 들어올 수 있는가. 권한이 하나라도 있으면 된다. */
    public boolean hasAccess() {
        return admin || canRead || canWrite;
    }

    /** 다른 회원의 공개 메모를 볼 수 있는가. */
    public boolean seesOthersPublic() {
        return admin || canRead;
    }

    /** 이 메모를 수정·삭제할 수 있는가. ADMIN 이 아니면 작성 권한이 있고 본인 메모여야 한다. */
    public boolean canEdit(KnowledgeNote note, Long viewerId) {
        return admin || (canWrite && note.isWrittenBy(viewerId));
    }
}
