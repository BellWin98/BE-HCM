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
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutRecordRepository extends JpaRepository<WorkoutRecord, Long> {

    @Query(
            """
                select wr
                from WorkoutRecord wr
                where wr.member = :member
                and wr.id = (
                    select max(wr2.id)
                    from WorkoutRecord wr2
                    where wr2.member = wr.member
                    and wr2.workoutDate = wr.workoutDate
                )
                order by wr.workoutDate desc
            """
    )
    List<WorkoutRecord> findAllByMemberPerWorkoutDate(@Param("member") Member member);

    @Query(
            """
                select wr
                from WorkoutRecord wr
                where wr.member = :member
                and wr.id = (
                    select max(wr2.id)
                    from WorkoutRecord wr2
                    where wr2.member = wr.member
                    and wr2.workoutDate = wr.workoutDate
                )
                order by wr.workoutDate desc
            """
    )
    Page<WorkoutRecord> findAllByMemberPerWorkoutDate(@Param("member") Member member, Pageable pageable);

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
                select wr.member.id, count(wr)
                from WorkoutRecord wr
                where wr.workoutRoom = :workoutRoom
                and wr.workoutDate >= :startDate and wr.workoutDate <= :endDate
                group by wr.member.id
            """
    )
    List<Object[]> countByWorkoutRoomAndWorkoutDateBetweenGroupByMember(
            @Param("workoutRoom") WorkoutRoom workoutRoom,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query(
            """
                select wr
                from WorkoutRecord wr
                where wr.member = :member
                and wr.workoutDate >= :startDate and wr.workoutDate <= :endDate
                and wr.id = (
                    select max(wr2.id)
                    from WorkoutRecord wr2
                    where wr2.member = wr.member
                    and wr2.workoutDate = wr.workoutDate
                )
                order by wr.workoutDate desc
            """
    )
    Page<WorkoutRecord> findAllByMemberPerWorkoutDateAndWorkoutDateBetween(
            @Param("member") Member member,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable
    );

    /**
     * 같은 날 저장된 회원의 인증을 방과 함께 모두 조회한다. 반환값은 (workoutDate, recordId, roomId, roomName).
     *
     * 운동 인증 한 번이 참여 중인 방 수만큼 레코드로 저장되므로, 하루 한 장만 그리는 피드가 대표 인증
     * 한 건만 보면 다른 방에 달린 리액션/댓글을 놓친다. 페이지에 실린 날짜를 한 번에 넘겨 형제 인증을
     * 모아 온다.
     */
    @Query(
            """
                select wr.workoutDate, wr.id, wr.workoutRoom.id, wr.workoutRoom.name
                from WorkoutRecord wr
                where wr.member = :member
                and wr.workoutDate in :workoutDates
                order by wr.workoutDate desc, wr.id asc
            """
    )
    List<Object[]> findRoomBreakdownByMemberAndWorkoutDateIn(
            @Param("member") Member member,
            @Param("workoutDates") Collection<LocalDate> workoutDates
    );

    void deleteByMember(Member member);

    void deleteByWorkoutRoom(WorkoutRoom workoutRoom);

    List<WorkoutRecord> findAllByWorkoutRoom(WorkoutRoom workoutRoom);

    /**
     * 운동방 하나에 남은 회원들의 운동 기록. 일자별로 추려내지 않는다 —
     * `uk_workout_record_member_room_date` 때문에 방 안에서는 (회원, 날짜)가 이미 유일하다.
     *
     * 반대로 방을 걸치는 `max(id)` 서브쿼리를 붙이면 안 된다. 인증 한 번이 방마다 한 건씩 저장되므로,
     * 그 회원의 그날 기록 중 다른 방 것이 뽑히면 이 방 목록에서 통째로 사라진다.
     */
    @Query(
            """
                select wr from WorkoutRecord wr
                where wr.workoutRoom = :workoutRoom
                and wr.member.id in :memberIds
                order by wr.workoutDate desc
            """
    )
    List<WorkoutRecord> findByWorkoutRoomAndMemberIn(@Param("workoutRoom") WorkoutRoom workoutRoom, @Param("memberIds") List<Long> memberIds);

    @Query(
            """
                select wr
                from WorkoutRecord wr
                where wr.member = :member
                and wr.workoutDate = :workoutDate
                and wr.workoutRoom in :workoutRooms
            """
    )
    List<WorkoutRecord> findByMemberAndWorkoutDateAndWorkoutRoomIn(
            @Param("member") Member member,
            @Param("workoutDate") LocalDate workoutDate,
            @Param("workoutRooms") List<WorkoutRoom> workoutRooms
    );

}
