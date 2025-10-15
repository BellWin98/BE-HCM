package com.behcm.domain.member.repository;

import com.behcm.domain.member.entity.MemberSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberSettingsRepository extends JpaRepository<MemberSettings, Long> {

    Optional<MemberSettings> findByMemberId(Long memberId);

    boolean existsByMemberId(Long memberId);
}
