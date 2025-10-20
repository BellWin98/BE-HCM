package com.behcm.domain.workout.repository;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.workout.entity.WorkoutRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutRoomRepository extends JpaRepository<WorkoutRoom, Long> {

    @Query("SELECT wr FROM WorkoutRoom wr JOIN wr.workoutRoomMembers wrm WHERE wrm.member = :member AND wr.isActive = true")
    Optional<WorkoutRoom> findActiveWorkoutRoomByMember(@Param("member") Member member);

    Optional<WorkoutRoom> findFirstByWorkoutRoomMembersMemberAndIsActiveTrue(Member member);

    @Query("SELECT wr FROM WorkoutRoom wr WHERE wr.isActive = true AND (wr.endDate IS NULL OR wr.endDate >= CURRENT_DATE)")
    List<WorkoutRoom> findActiveRooms();

    List<WorkoutRoom> findByIsActiveTrue();

    @Query("SELECT wr FROM WorkoutRoom wr " +
           "JOIN FETCH wr.owner " +
           "LEFT JOIN FETCH wr.workoutRoomMembers wrm " +
           "LEFT JOIN FETCH wrm.member " +
           "WHERE wr.isActive = true")
    List<WorkoutRoom> findByIsActiveTrueWithFetch();
    Optional<WorkoutRoom> findByEntryCode(String entryCode);
    Optional<WorkoutRoom> findByIdAndIsActiveTrue(Long roomId);
}
