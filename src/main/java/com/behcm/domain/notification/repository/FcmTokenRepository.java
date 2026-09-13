package com.behcm.domain.notification.repository;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.notification.entity.FcmToken;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    Optional<FcmToken> findByMember(Member member);

    @Query("SELECT f.token FROM FcmToken f WHERE f.member IN :members")
    List<String> findFcmTokensByMembers(@Param("members") List<Member> members);

    void deleteByMember(Member member);

    // 비동기 발송 스레드(트랜잭션 밖)에서 호출되므로 여기서 트랜잭션을 연다.
    // SimpleJpaRepository 의 readOnly 트랜잭션을 그대로 타면 delete 가 flush 되지 않는다.
    @Transactional
    void deleteByToken(String token);
}