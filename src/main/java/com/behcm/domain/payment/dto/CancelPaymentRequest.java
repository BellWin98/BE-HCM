package com.behcm.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CancelPaymentRequest {

    @NotBlank(message = "결제 키는 필수입니다.")
    private String paymentKey;

    @NotBlank(message = "취소 사유는 필수입니다.")
    private String cancelReason;

    public CancelPaymentRequest(String paymentKey, String cancelReason) {
        this.paymentKey = paymentKey;
        this.cancelReason = cancelReason;
    }
}