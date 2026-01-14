package com.behcm.domain.rest.entity;

import com.behcm.domain.workout.entity.WorkoutRoomMember;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;

@Entity
@Table(
        indexes = {
                @Index(name = "idx_rest_workout_room_member", columnList = "workout_room_member_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Rest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_room_member_id", nullable = false)
    private WorkoutRoomMember workoutRoomMember;

    @Column(nullable = false, length = 20)
    private String reason;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Builder
    public Rest(WorkoutRoomMember workoutRoomMember, String reason, LocalDate startDate, LocalDate endDate) {
        this.workoutRoomMember = workoutRoomMember;
        this.reason = reason;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public boolean isActive() {
        LocalDate now = LocalDate.now();
        return !now.isBefore(startDate) && !now.isAfter(endDate);
    }

}
