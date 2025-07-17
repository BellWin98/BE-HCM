package com.behcm.domain.workout.repository;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.entity.WorkoutRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutRecordRepository extends JpaRepository<WorkoutRecord, Long> {
    List<WorkoutRecord> findAllByMember(Member member);
    Optional<WorkoutRecord> findByMemberAndWorkoutDate(Member member, LocalDate today);
    boolean existsByMemberAndWorkoutRoomAndWorkoutDate(Member member, WorkoutRoom workoutRoom, LocalDate workoutDate);
}
