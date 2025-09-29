package com.behcm.domain.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreatePaymentOrderRequest {

    @NotNull(message = "벌금 기록 ID는 필수입니다.")
    private Long penaltyRecordId;

    @NotNull(message = "결제 금액은 필수입니다.")
    @Min(value = 1, message = "결제 금액은 1원 이상이어야 합니다.")
    private Long amount;

    @NotBlank(message = "주문 ID는 필수입니다.")
    private String orderId;

    public CreatePaymentOrderRequest(Long penaltyRecordId, Long amount, String orderId) {
        this.penaltyRecordId = penaltyRecordId;
        this.amount = amount;
        this.orderId = orderId;
    }
}