package com.behcm.domain.penalty.entity;

import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String bankName;

    @Column(nullable = false, length = 30)
    private String accountNumber;

    @Column(nullable = false, length = 30)
    private String holderName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_room_id", nullable = false)
    private WorkoutRoom workoutRoom;

    @Builder
    public BankAccount(String bankName, String accountNumber, String holderName, WorkoutRoom workoutRoom) {
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.holderName = holderName;
        this.workoutRoom = workoutRoom;
    }

    public void updateAccountInfo(String bankName, String accountNumber, String holderName) {
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.holderName = holderName;
    }
}