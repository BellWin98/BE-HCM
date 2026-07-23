package com.behcm.domain.penalty.entity;

import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(uniqueConstraints = {
        // 방당 계좌는 1건(findByWorkoutRoom 이 Optional 을 반환하므로 코드가 이미 1:1 을 전제한다).
        @UniqueConstraint(
                name = "uk_penalty_account_room",
                columnNames = {"workout_room_id"}
        )
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PenaltyAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String bankName;

    @Column(nullable = false, length = 30)
    private String accountNumber;

    @Column(nullable = false, length = 30)
    private String accountHolder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_room_id", nullable = false)
    private WorkoutRoom workoutRoom;

    @Builder
    public PenaltyAccount(String bankName, String accountNumber, String accountHolder, WorkoutRoom workoutRoom) {
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.workoutRoom = workoutRoom;
    }

    public void updateAccountInfo(String bankName, String accountNumber, String accountHolder) {
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
    }
}