package com.behcm.domain.member.repository;

import com.behcm.domain.member.entity.MemberSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberSettingsRepository extends JpaRepository<MemberSettings, Long> {
    Optional<MemberSettings> findByMemberId(Long memberId);
}
