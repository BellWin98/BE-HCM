package com.behcm.domain.rest.repository;

import com.behcm.domain.rest.entity.Rest;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RestRepository extends JpaRepository<Rest, Long> {
    List<Rest> findAllByWorkoutRoomMember(WorkoutRoomMember workoutRoomMember);
    List<Rest> findAllByWorkoutRoomMemberIn(List<WorkoutRoomMember> workoutRoomMembers);
}
