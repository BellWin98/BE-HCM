package com.behcm.domain.penalty.repository;

import com.behcm.domain.penalty.entity.PenaltyAccount;
import com.behcm.domain.workout.entity.WorkoutRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PenaltyAccountRepository extends JpaRepository<PenaltyAccount, Long> {
    Optional<PenaltyAccount> findByWorkoutRoom(WorkoutRoom workoutRoom);
}