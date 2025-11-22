package com.behcm.domain.workout.repository;

import com.behcm.domain.workout.entity.WorkoutLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkoutLikeRepository extends JpaRepository<WorkoutLike, Long> {

    Optional<WorkoutLike> findByMemberIdAndWorkoutRecordId(Long memberId, Long workoutRecordId);

    boolean existsByMemberIdAndWorkoutRecordId(Long memberId, Long workoutRecordId);

    long countByWorkoutRecordId(Long workoutRecordId);

    void deleteByMemberIdAndWorkoutRecordId(Long memberId, Long workoutRecordId);

    @Query("SELECT wl.workoutRecord.id FROM WorkoutLike wl WHERE wl.member.id = :memberId AND wl.workoutRecord.id IN :workoutRecordIds")
    List<Long> findLikedWorkoutRecordIdsByMemberIdAndWorkoutRecordIds(@Param("memberId") Long memberId, @Param("workoutRecordIds") List<Long> workoutRecordIds);
}
