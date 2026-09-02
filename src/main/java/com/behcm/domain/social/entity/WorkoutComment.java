package com.behcm.domain.social.entity;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 운동 인증에 달린 댓글. 대댓글 없이 시간순으로 나열되는 평면 구조다.
 */
@Entity
@Table(name = "workout_comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkoutComment extends BaseTimeEntity {

    public static final int MAX_CONTENT_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_record_id", nullable = false)
    private WorkoutRecord workoutRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Builder
    public WorkoutComment(WorkoutRecord workoutRecord, Member member, String content) {
        this.workoutRecord = workoutRecord;
        this.member = member;
        this.content = content;
    }

    public boolean isWrittenBy(Member member) {
        return this.member.getId() != null && this.member.getId().equals(member.getId());
    }
}
