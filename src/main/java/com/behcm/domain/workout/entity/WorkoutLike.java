package com.behcm.domain.workout.entity;

import com.behcm.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "workout_like", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"member_id", "workout_record_id"})
})
public class WorkoutLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_record_id", nullable = false)
    private WorkoutRecord workoutRecord;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public WorkoutLike(Member member, WorkoutRecord workoutRecord) {
        this.member = member;
        this.workoutRecord = workoutRecord;
    }
}
