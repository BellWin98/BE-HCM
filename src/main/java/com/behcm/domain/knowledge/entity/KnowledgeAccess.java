package com.behcm.domain.knowledge.entity;

import com.behcm.domain.member.entity.Member;
import com.behcm.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지식 메모에 대한 회원별 읽기/쓰기 권한.
 *
 * <p>ADMIN 은 이 테이블에 행이 없어도 항상 읽기/쓰기가 가능하고({@code KnowledgeAccessChecker}),
 * 그 외 회원은 여기에 등록된 권한만 가진다. 두 권한이 모두 false 가 되면 행을 두지 않고 삭제한다.
 *
 * <p>{@code grantedBy} 는 부여한 관리자의 id 를 FK 없이 보관한다 — 감사 흔적일 뿐이므로
 * 참조 무결성을 요구하지 않는다.
 */
@Entity
@Table(
        name = "knowledge_access",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_knowledge_access_member",
                        columnNames = {"member_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnowledgeAccess extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "can_read", nullable = false)
    private boolean canRead;

    @Column(name = "can_write", nullable = false)
    private boolean canWrite;

    @Column(name = "granted_by")
    private Long grantedBy;

    @Builder
    public KnowledgeAccess(Member member, boolean canRead, boolean canWrite, Long grantedBy) {
        this.member = member;
        this.canRead = canRead;
        this.canWrite = canWrite;
        this.grantedBy = grantedBy;
    }
}
