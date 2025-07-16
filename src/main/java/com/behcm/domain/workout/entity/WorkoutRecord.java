package com.behcm.domain.workout.entity;

import com.behcm.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class WorkoutRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_room_id", nullable = false)
    private WorkoutRoom workoutRoom;

    @Column(nullable = false)
    private String workoutType;

    @Column(nullable = false)
    private LocalDate workoutDate;

    @Column(nullable = false)
    private Integer duration; // minutes

    @Column(nullable = false)
    private String imageUrl;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public WorkoutRecord(Member member, WorkoutRoom workoutRoom, LocalDate workoutDate,
                         String workoutType, Integer duration, String imageUrl) {
        this.member = member;
        this.workoutRoom = workoutRoom;
        this.workoutDate = workoutDate;
        this.workoutType = workoutType;
        this.duration = duration;
        this.imageUrl = imageUrl;
    }

    public boolean canDelete() {
        return workoutDate.equals(LocalDate.now());
    }
}
