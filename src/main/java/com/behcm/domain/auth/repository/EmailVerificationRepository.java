package com.behcm.domain.auth.repository;

import com.behcm.domain.auth.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long>{

    // 가장 최근 미인증 코드 조회
    Optional<EmailVerification> findFirstByEmailAndIsVerifiedFalseOrderByCreatedAtDesc(String email);

    void deleteByEmailAndIsVerifiedFalse(String email);
}
