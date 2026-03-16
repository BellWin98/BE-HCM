package com.behcm.domain.penalty.repository;

import com.behcm.domain.penalty.entity.Penalty;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PenaltyRepository extends JpaRepository<Penalty, Long> {

    @Query("SELECT p FROM Penalty p WHERE p.workoutRoomMember.workoutRoom.id = :roomId")
    List<Penalty> findAllByWorkoutRoomId(@Param("roomId") Long roomId);

    @Query("SELECT p FROM Penalty p WHERE p.workoutRoomMember.workoutRoom.id = :roomId " +
            "AND p.weekStartDate <= :endDate AND p.weekEndDate >= :startDate")
    List<Penalty> findAllByWorkoutRoomIdAndWeekOverlapping(
            @Param("roomId") Long roomId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    void deleteAllByWorkoutRoomMemberIn(List<WorkoutRoomMember> workoutRoomMembers);

    void deleteAllByWorkoutRoomMember(WorkoutRoomMember workoutRoomMember);
}