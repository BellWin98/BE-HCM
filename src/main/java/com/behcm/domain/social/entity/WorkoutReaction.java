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
 * 운동 인증에 대한 이모지 리액션.
 *
 * 한 회원은 하나의 인증에 이모지를 하나만 남길 수 있다(UK). 다른 이모지를 누르면
 * 새 행이 생기는 것이 아니라 기존 행의 이모지가 바뀐다.
 */
@Entity
@Table(
        name = "workout_reaction",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_workout_reaction_record_member",
                        columnNames = {"workout_record_id", "member_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkoutReaction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_record_id", nullable = false)
    private WorkoutRecord workoutRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReactionEmoji emoji;

    @Builder
    public WorkoutReaction(WorkoutRecord workoutRecord, Member member, ReactionEmoji emoji) {
        this.workoutRecord = workoutRecord;
        this.member = member;
        this.emoji = emoji;
    }

    public void changeEmoji(ReactionEmoji emoji) {
        this.emoji = emoji;
    }
}
