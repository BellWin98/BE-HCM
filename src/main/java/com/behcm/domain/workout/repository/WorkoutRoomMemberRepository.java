package com.behcm.domain.workout.repository;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkoutRoomMemberRepository extends JpaRepository<WorkoutRoomMember, Long> {
    List<WorkoutRoomMember> findByWorkoutRoomOrderByJoinedAt(WorkoutRoom workoutRoom);

    @Query("SELECT wrm FROM WorkoutRoomMember wrm JOIN FETCH wrm.member WHERE wrm.workoutRoom = :workoutRoom ORDER BY wrm.joinedAt")
    List<WorkoutRoomMember> findByWorkoutRoomOrderByJoinedAtFetchMember(@Param("workoutRoom") WorkoutRoom workoutRoom);
    boolean existsByMemberAndWorkoutRoom(Member member, WorkoutRoom workoutRoom);
    List<WorkoutRoomMember> findWorkoutRoomMembersByMember(Member member);
}
