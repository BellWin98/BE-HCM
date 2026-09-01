package com.behcm.domain.social.repository;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.social.entity.WorkoutComment;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.entity.WorkoutRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface WorkoutCommentRepository extends JpaRepository<WorkoutComment, Long> {

    @Query(
            value = """
                    select c from WorkoutComment c
                    join fetch c.member
                    where c.workoutRecord = :workoutRecord
                    order by c.createdAt asc, c.id asc
                    """,
            countQuery = "select count(c) from WorkoutComment c where c.workoutRecord = :workoutRecord"
    )
    Page<WorkoutComment> findAllByWorkoutRecordFetchMember(
            @Param("workoutRecord") WorkoutRecord workoutRecord,
            Pageable pageable
    );

    /**
     * 인증 여러 건의 댓글 수를 한 번에 집계한다. 반환값은 (recordId, count).
     * 댓글이 0건인 인증은 결과에 나타나지 않으므로 호출부에서 기본값 0 을 채워야 한다.
     */
    @Query("""
            select c.workoutRecord.id, count(c)
            from WorkoutComment c
            where c.workoutRecord.id in :recordIds
            group by c.workoutRecord.id
            """)
    List<Object[]> countByRecordIds(@Param("recordIds") Collection<Long> recordIds);

    /**
     * 회원 탈퇴 정리용. 본인이 쓴 댓글과 본인 인증에 달린 남의 댓글을 함께 지운다.
     * workout_record 를 지우기 전에 호출해야 FK 제약에 걸리지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from WorkoutComment c
            where c.member = :member
            or c.workoutRecord in (select wr from WorkoutRecord wr where wr.member = :member)
            """)
    void deleteAllByMemberOrRecordOwner(@Param("member") Member member);

    /**
     * 운동방 삭제 정리용. workout_record 를 지우기 전에 호출해야 한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from WorkoutComment c
            where c.workoutRecord in (select wr from WorkoutRecord wr where wr.workoutRoom = :workoutRoom)
            """)
    void deleteAllByWorkoutRoom(@Param("workoutRoom") WorkoutRoom workoutRoom);
}
