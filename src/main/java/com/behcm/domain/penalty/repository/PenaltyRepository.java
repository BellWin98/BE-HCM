package com.behcm.domain.penalty.repository;

import com.behcm.domain.penalty.entity.Penalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PenaltyRepository extends JpaRepository<Penalty, Long> {

    @Query("SELECT p FROM Penalty p WHERE p.workoutRoomMember.workoutRoom.id = :roomId AND p.isPaid = false")
    List<Penalty> findUnpaidByRoomId(@Param("roomId") Long roomId);

    @Query("SELECT p FROM Penalty p WHERE p.isPaid = false")
    List<Penalty> findAllUnpaid();

    @Query("SELECT p FROM Penalty p WHERE p.id IN :ids")
    List<Penalty> findByIds(@Param("ids") List<Long> ids);

    @Query("SELECT p FROM Penalty p WHERE p.workoutRoomMember.workoutRoom.id = :roomId")
    List<Penalty> findAllByWorkoutRoomId(@Param("roomId") Long roomId);
}