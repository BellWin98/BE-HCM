package com.behcm.domain.tossstock.entity;

import com.behcm.domain.member.entity.Member;
import com.behcm.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 토스증권 화면/API 에 대한 개별 접근 권한.
 *
 * <p>토스 접근은 {@code MemberRole} 에서 분리되어 있다. {@code FAMILY} 는 한국투자증권({@code /api/stock})
 * 까지 함께 여는 역할이라 "토스만 허용"을 표현할 수 없고, role 은 단일 값 컬럼이라 회원별 지정도 불가능하다.
 * ADMIN 은 이 테이블에 행이 없어도 항상 접근할 수 있고(={@code TossAccessChecker}), 그 외 회원은
 * 여기에 등록된 경우에만 접근한다.
 *
 * <p>{@code grantedBy} 는 부여한 관리자의 id 를 FK 없이 보관한다. FK 를 걸면 그 관리자를 삭제할 때
 * {@code AdminMemberService.deleteMember} 에 정리 경로가 하나 더 늘어난다 — 감사 흔적일 뿐이므로
 * 참조 무결성을 요구하지 않는다.
 */
@Entity
@Table(
        name = "toss_access",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_toss_access_member",
                        columnNames = {"member_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TossAccess extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "granted_by")
    private Long grantedBy;

    @Builder
    public TossAccess(Member member, Long grantedBy) {
        this.member = member;
        this.grantedBy = grantedBy;
    }
}
