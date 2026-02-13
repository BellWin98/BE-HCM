package com.behcm.domain.member.repository;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    Optional<Member> findByNickname(String nickname);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<Member> findByOauthProviderAndOauthProviderId(String oauthProvider, String oauthProviderId);

    @Query("SELECT m FROM Member m " +
            "WHERE (:role IS NULL OR m.role = :role) " +
            "AND (:query IS NULL OR LOWER(m.nickname) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(m.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Member> searchAdminMembers(@Param("query") String query,
                                    @Param("role") MemberRole role,
                                    Pageable pageable);

    @Query("SELECT m FROM Member m WHERE m.nickname LIKE %:nickname%")
    List<Member> findByNicknameContaining(@Param("nickname") String nickname);
}
