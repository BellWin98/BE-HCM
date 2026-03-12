package com.behcm.domain.workout.repository;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutRoomMemberRepository extends JpaRepository<WorkoutRoomMember, Long> {
    boolean existsByMemberAndWorkoutRoom(Member member, WorkoutRoom workoutRoom);
    Optional<WorkoutRoomMember> findByWorkoutRoomAndMember(WorkoutRoom workoutRoom, Member member);
    List<WorkoutRoomMember> findByWorkoutRoomOrderByJoinedAt(WorkoutRoom workoutRoom);
    List<WorkoutRoomMember> findByMember(Member member);

    @Query("SELECT wrm FROM WorkoutRoomMember wrm JOIN FETCH wrm.member WHERE wrm.workoutRoom = :workoutRoom ORDER BY wrm.joinedAt")
    List<WorkoutRoomMember> findByWorkoutRoomOrderByJoinedAtFetchMember(@Param("workoutRoom") WorkoutRoom workoutRoom);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update WorkoutRoomMember wrm set wrm.weeklyWorkouts = 0 where wrm.weeklyWorkouts <> 0")
    void resetWeeklyWorkouts();
}
