package com.behcm.domain.penalty.entity;

import com.behcm.domain.workout.entity.WorkoutRoomMember;
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
public class Penalty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long penaltyAmount;

    @Column(nullable = false)
    private Integer requiredWorkouts;

    @Column(nullable = false)
    private Integer actualWorkouts;

    @Column(nullable = false)
    private LocalDate weekStartDate;

    @Column(nullable = false)
    private LocalDate weekEndDate;

    @Column(nullable = false)
    private Boolean isPaid = false;

    private LocalDateTime paidAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_room_member_id", nullable = false)
    private WorkoutRoomMember workoutRoomMember;

    @Builder
    public Penalty(
            WorkoutRoomMember workoutRoomMember, Long penaltyAmount, Integer requiredWorkouts,
            Integer actualWorkouts, LocalDate weekStartDate, LocalDate weekEndDate
    ) {
        this.workoutRoomMember = workoutRoomMember;
        this.weekStartDate = weekStartDate;
        this.weekEndDate = weekEndDate;
        this.requiredWorkouts = requiredWorkouts;
        this.actualWorkouts = actualWorkouts;
        this.penaltyAmount = penaltyAmount;
    }

    public void markAsPaid() {
        this.isPaid = true;
        this.paidAt = LocalDateTime.now();
    }
}
