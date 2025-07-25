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
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"member_id", "workout_room_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class WorkoutRoomMember {

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
    private Integer totalWorkouts = 0;

    @Column(nullable = false)
    private Integer weeklyWorkouts = 0;

    @Column(nullable = false)
    private Long totalPenalty = 0L;

    @Column(nullable = false)
    private Boolean isOnBreak = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Builder
    public WorkoutRoomMember(Member member, WorkoutRoom workoutRoom) {
        this.member = member;
        this.workoutRoom = workoutRoom;
    }

    public void updateWeeklyWorkouts(int weeklyWorkouts) {
        this.weeklyWorkouts = weeklyWorkouts;
    }

    public void updateTotalWorkouts(int totalWorkouts) {
        this.totalWorkouts = totalWorkouts;
    }

    public void updateTotalPenalty(Long totalPenalty) {
        this.totalPenalty = totalPenalty;
    }

    public void setBreakStatus(boolean isOnBreak) {
        this.isOnBreak = isOnBreak;
    }

    public void resetWeeklyWorkouts() {
        this.weeklyWorkouts = 0;
    }

    public String getNickname() {
        return member.getNickname();
    }
}
