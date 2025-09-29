package com.behcm.domain.payment.dto;

import com.behcm.domain.payment.entity.Payment;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class CancelPaymentResponse {

    private Long paymentId;
    private String paymentKey;
    private String orderId;
    private BigDecimal amount;
    private String status;
    private String cancelReason;
    private LocalDateTime canceledAt;

    public static CancelPaymentResponse from(Payment payment, String cancelReason) {
        return CancelPaymentResponse.builder()
                .paymentId(payment.getId())
                .paymentKey(payment.getPaymentKey())
                .orderId(payment.getOrderId())
                .amount(payment.getAmount())
                .status(payment.getStatus().name())
                .cancelReason(cancelReason)
                .canceledAt(payment.getUpdatedAt())
                .build();
    }
}