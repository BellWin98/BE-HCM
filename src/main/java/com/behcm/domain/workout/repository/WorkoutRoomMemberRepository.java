package com.behcm.domain.workout.repository;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutRoomMemberRepository extends JpaRepository<WorkoutRoomMember, Long> {
    List<WorkoutRoomMember> findByWorkoutRoomOrderByJoinedAt(WorkoutRoom workoutRoom);
    List<WorkoutRoomMember> findByWorkoutRoomAndMemberNotOrderByJoinedAt(WorkoutRoom workoutRoom, Member member);
    boolean existsByMemberAndWorkoutRoom(Member member, WorkoutRoom workoutRoom);
    Optional<WorkoutRoomMember> findByMember(Member member);
}
