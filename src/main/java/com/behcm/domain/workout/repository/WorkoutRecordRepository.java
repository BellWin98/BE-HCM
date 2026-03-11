package com.behcm.domain.workout.repository;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.entity.WorkoutRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutRecordRepository extends JpaRepository<WorkoutRecord, Long> {

    @Query(
            """
                select wr
                from WorkoutRecord wr
                where wr.member = :member
                and wr.createdAt = (
                    select max(wr2.createdAt)
                    from WorkoutRecord wr2
                    where wr2.member = wr.member
                    and wr2.workoutDate = wr.workoutDate
                )
                order by wr.workoutDate desc
            """
    )
    List<WorkoutRecord> findAllByMemberPerWorkoutDate(Member member);

    Optional<WorkoutRecord> findByMemberAndWorkoutRoomAndWorkoutDate(Member member, WorkoutRoom workoutRoom, LocalDate today);
    boolean existsByMemberAndWorkoutRoomAndWorkoutDate(Member member, WorkoutRoom workoutRoom, LocalDate workoutDate);

    @Query(
            """
                select count(wr)
                from WorkoutRecord wr
                where wr.member = :member
                and wr.workoutRoom = :workoutRoom
                and wr.workoutDate >= :startDate and wr.workoutDate <= :endDate
            """
    )
    long countByMemberAndWorkoutRoomAndWorkoutDateBetween(
            @Param("member") Member member,
            @Param("workoutRoom") WorkoutRoom workoutRoom,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query(
            """
                select wr
                from WorkoutRecord wr
                where wr.member = :member
                and wr.createdAt = (
                    select max(wr2.createdAt)
                    from WorkoutRecord wr2
                    where wr2.member = wr.member
                    and wr2.workoutDate = wr.workoutDate
                )
                order by wr.workoutDate desc
            """
    )
    Page<WorkoutRecord> findAllByMemberPerWorkoutDate(Member member, Pageable pageable);

    void deleteByMember(Member member);

    void deleteByWorkoutRoom(WorkoutRoom workoutRoom);

    List<WorkoutRecord> findAllByWorkoutRoom(WorkoutRoom workoutRoom);

    @Query(
            """
                select wr from WorkoutRecord wr
                where 1=1
                and wr.workoutRoom = :workoutRoom
                and wr.member.id in :memberIds
                order by wr.workoutDate desc
            """
    )
    List<WorkoutRecord> findByWorkoutRoomAndMemberIn(@Param("workoutRoom") WorkoutRoom workoutRoom, @Param("memberIds") List<Long> memberIds);

}
