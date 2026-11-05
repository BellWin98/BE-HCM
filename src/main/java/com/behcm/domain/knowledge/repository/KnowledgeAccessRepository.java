package com.behcm.domain.knowledge.repository;

import com.behcm.domain.knowledge.entity.KnowledgeAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface KnowledgeAccessRepository extends JpaRepository<KnowledgeAccess, Long> {

    Optional<KnowledgeAccess> findByMemberId(Long memberId);

    List<KnowledgeAccess> findByMemberIdIn(Collection<Long> memberIds);

    void deleteByMemberId(Long memberId);

    @Query("SELECT ka FROM KnowledgeAccess ka JOIN FETCH ka.member ORDER BY ka.updatedAt DESC")
    List<KnowledgeAccess> findAllWithMember();

    /**
     * 동시 요청에서도 uk_knowledge_access_member 충돌 없이 한 행으로 수렴하도록 단일 SQL 로 삽입/갱신한다.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "INSERT INTO knowledge_access (created_at, updated_at, member_id, can_read, can_write, granted_by) "
            + "VALUES (NOW(6), NOW(6), :memberId, :canRead, :canWrite, :grantedBy) "
            + "ON DUPLICATE KEY UPDATE can_read = VALUES(can_read), can_write = VALUES(can_write), "
            + "granted_by = VALUES(granted_by), updated_at = NOW(6)", nativeQuery = true)
    int upsert(@Param("memberId") Long memberId,
               @Param("canRead") boolean canRead,
               @Param("canWrite") boolean canWrite,
               @Param("grantedBy") Long grantedBy);
}
