package com.behcm.domain.workout.repository;

import com.behcm.domain.workout.entity.WorkoutRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkoutRecordRepository extends JpaRepository<WorkoutRecord, Long>{
}
