package com.behcm.domain.penalty.repository;

import com.behcm.domain.penalty.entity.BankAccount;
import com.behcm.domain.workout.entity.WorkoutRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    Optional<BankAccount> findByWorkoutRoom(WorkoutRoom workoutRoom);
}