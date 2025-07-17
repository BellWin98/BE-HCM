package com.behcm.domain.rest.repository;

import com.behcm.domain.rest.entity.RestInfo;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RestInfoRepository extends JpaRepository<RestInfo, Long> {
    List<RestInfo> findAllByWorkoutRoomMember(WorkoutRoomMember workoutRoomMember);
}
