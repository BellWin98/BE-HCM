package com.behcm.domain.social.repository;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.social.entity.WorkoutReaction;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.entity.WorkoutRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutReactionRepository extends JpaRepository<WorkoutReaction, Long> {

    Optional<WorkoutReaction> findByWorkoutRecordAndMember(WorkoutRecord workoutRecord, Member member);

    @Query("""
            select r from WorkoutReaction r
            join fetch r.member
            where r.workoutRecord = :workoutRecord
            order by r.createdAt
            """)
    List<WorkoutReaction> findAllByWorkoutRecordFetchMember(@Param("workoutRecord") WorkoutRecord workoutRecord);

    /**
     * 인증 여러 건의 이모지별 리액션 수를 한 번에 집계한다. 반환값은 (recordId, emoji, count).
     * 피드/운동방 상세가 화면 하나에 인증 수십 건을 그리므로 건별 조회를 하면 그대로 N+1 이 된다.
     */
    @Query("""
            select r.workoutRecord.id, r.emoji, count(r)
            from WorkoutReaction r
            where r.workoutRecord.id in :recordIds
            group by r.workoutRecord.id, r.emoji
            """)
    List<Object[]> countByRecordIdsGroupByEmoji(@Param("recordIds") Collection<Long> recordIds);

    /**
     * 인증 여러 건의 리액션을 (recordId, memberId, emoji) 행 그대로 조회한다.
     *
     * 같은 운동이 방마다 별도 레코드로 저장되므로, 형제 레코드를 합쳐 셀 때는 회원 기준 중복 제거가
     * 필요하다. 집계 전 행이 필요한 {@link com.behcm.domain.social.service.WorkoutSocialQueryService}
     * 의 그룹 집계 경로에서만 쓴다.
     */
    @Query("""
            select r.workoutRecord.id, r.member.id, r.emoji
            from WorkoutReaction r
            where r.workoutRecord.id in :recordIds
            """)
    List<Object[]> findMemberEmojiByRecordIds(@Param("recordIds") Collection<Long> recordIds);

    /**
     * 조회 주체가 각 인증에 어떤 이모지를 눌렀는지 한 번에 조회한다. 반환값은 (recordId, emoji).
     */
    @Query("""
            select r.workoutRecord.id, r.emoji
            from WorkoutReaction r
            where r.workoutRecord.id in :recordIds
            and r.member = :member
            """)
    List<Object[]> findEmojiByRecordIdsAndMember(
            @Param("recordIds") Collection<Long> recordIds,
            @Param("member") Member member
    );

    /**
     * 회원 탈퇴 정리용. 본인이 남긴 리액션과 본인 인증에 달린 남의 리액션을 함께 지운다.
     * workout_record 를 지우기 전에 호출해야 FK 제약에 걸리지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from WorkoutReaction r
            where r.member = :member
            or r.workoutRecord in (select wr from WorkoutRecord wr where wr.member = :member)
            """)
    void deleteAllByMemberOrRecordOwner(@Param("member") Member member);

    /**
     * 운동방 삭제 정리용. workout_record 를 지우기 전에 호출해야 한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from WorkoutReaction r
            where r.workoutRecord in (select wr from WorkoutRecord wr where wr.workoutRoom = :workoutRoom)
            """)
    void deleteAllByWorkoutRoom(@Param("workoutRoom") WorkoutRoom workoutRoom);
}
