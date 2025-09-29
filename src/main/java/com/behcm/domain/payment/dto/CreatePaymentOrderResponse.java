package com.behcm.domain.payment.dto;

import com.behcm.domain.payment.entity.Payment;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class CreatePaymentOrderResponse {

    private Long paymentId;
    private String orderId;
    private BigDecimal amount;
    private String status;

    public static CreatePaymentOrderResponse from(Payment payment) {
        return CreatePaymentOrderResponse.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .amount(payment.getAmount())
                .status(payment.getStatus().name())
                .build();
    }
}