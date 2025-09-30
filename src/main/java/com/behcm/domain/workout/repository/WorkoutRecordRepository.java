package com.behcm.domain.workout.repository;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.entity.WorkoutRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutRecordRepository extends JpaRepository<WorkoutRecord, Long> {
    List<WorkoutRecord> findAllByMember(Member member);
    Optional<WorkoutRecord> findByMemberAndWorkoutRoomAndWorkoutDate(Member member, WorkoutRoom workoutRoom, LocalDate today);
    boolean existsByMemberAndWorkoutRoomAndWorkoutDate(Member member, WorkoutRoom workoutRoom, LocalDate workoutDate);

    @Query("SELECT COUNT(wr) FROM WorkoutRecord wr WHERE wr.member = :member AND wr.workoutRoom = :workoutRoom AND wr.workoutDate >= :startDate AND wr.workoutDate <= :endDate")
    long countByMemberAndWorkoutRoomAndWorkoutDateBetween(@Param("member") Member member, @Param("workoutRoom") WorkoutRoom workoutRoom, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
