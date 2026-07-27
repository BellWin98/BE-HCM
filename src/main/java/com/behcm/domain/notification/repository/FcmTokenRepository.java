package com.behcm.domain.notification.repository;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.notification.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    Optional<FcmToken> findByMember(Member member);

    Optional<FcmToken> findByToken(String token);

    @Query("SELECT f.token FROM FcmToken f WHERE f.member IN :members")
    List<String> findFcmTokensByMembers(@Param("members") List<Member> members);

    @Query(
            """
                select ft.token
                from WorkoutRoomMember me
                join WorkoutRoomMember other on me.workoutRoom = other.workoutRoom
                join FcmToken ft on ft.member = other.member
                where me.member = :member
                and other.member != :member
            """
    )
    List<String> findFcmTokensByMember(@Param("member") Member member);

    void deleteByMember(Member member);

    @Modifying
    @Query("delete from FcmToken f where f.token = :token")
    void deleteByToken(@Param("token") String token);

    @Modifying
    @Query("delete from FcmToken f where f.token in :tokens")
    void deleteByTokenIn(@Param("tokens") List<String> tokens);
}
