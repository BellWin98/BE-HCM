package com.behcm.domain.payment.entity;

import com.behcm.domain.penalty.entity.Penalty;
import com.behcm.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderId;

    @Column(unique = true)
    private String paymentKey;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private String method;

    private String failReason;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "penalty_id", nullable = false)
    private Penalty penalty;

    @Builder
    public Payment(String orderId, String paymentKey, BigDecimal amount, PaymentStatus status,
                   String method, Penalty penalty, String failReason) {
        this.orderId = orderId;
        this.paymentKey = paymentKey;
        this.amount = amount;
        this.status = status;
        this.method = method;
        this.penalty = penalty;
        this.failReason = failReason;
    }

    public void updateStatus(PaymentStatus status) {
        this.status = status;
    }

    public void updatePaymentKey(String paymentKey) {
        this.paymentKey = paymentKey;
    }

    public void updateFailReason(String failReason) {
        this.failReason = failReason;
    }

    public boolean isCompleted() {
        return this.status == PaymentStatus.DONE;
    }

    public boolean isCanceled() {
        return this.status == PaymentStatus.CANCELED;
    }
}