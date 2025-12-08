package com.behcm.domain.notification.repository;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.notification.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    Optional<FcmToken> findByToken(String token);

    Optional<FcmToken> findByMemberAndIsActiveTrue(Member member);

    List<FcmToken> findByMemberInAndIsActiveTrue(List<Member> members);

    @Query("SELECT f FROM FcmToken f WHERE f.member.id IN " +
           "(SELECT wrm.member.id FROM WorkoutRoomMember wrm WHERE wrm.workoutRoom.id = :roomId " +
           "AND wrm.member.id != :excludeMemberId) AND f.isActive = true")
    List<FcmToken> findActiveTokensByRoomIdExcludingMember(@Param("roomId") Long roomId,
                                                            @Param("excludeMemberId") Long excludeMemberId);
}