package com.behcm.domain.workout.repository;

import com.behcm.domain.workout.entity.WorkoutComment;
import com.behcm.domain.workout.entity.WorkoutRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutCommentRepository extends JpaRepository<WorkoutComment, Long> {

    List<WorkoutComment> findByWorkoutRecordIdOrderByCreatedAtAsc(Long workoutRecordId);

    long countByWorkoutRecordId(Long workoutRecordId);

    Optional<WorkoutComment> findByIdAndMemberId(Long id, Long memberId);

    void deleteByWorkoutRecord(WorkoutRecord workoutRecord);
}
