package com.behcm.domain.tossstock.repository;

import com.behcm.domain.tossstock.entity.TossAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TossAccessRepository extends JpaRepository<TossAccess, Long> {

    boolean existsByMemberId(Long memberId);

    Optional<TossAccess> findByMemberId(Long memberId);

    void deleteByMemberId(Long memberId);

    /**
     * 관리자 회원 목록에서 페이지 단위로 토글 상태를 채우기 위한 조회.
     * 건별 {@code existsByMemberId} 를 돌리면 그대로 N+1 이 된다.
     */
    @Query("SELECT ta.member.id FROM TossAccess ta WHERE ta.member.id IN :memberIds")
    Set<Long> findGrantedMemberIds(@Param("memberIds") Collection<Long> memberIds);

    @Query("SELECT ta FROM TossAccess ta JOIN FETCH ta.member ORDER BY ta.createdAt DESC")
    List<TossAccess> findAllWithMember();
}
