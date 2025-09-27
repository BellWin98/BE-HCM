package com.behcm.domain.penalty.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PayPenaltyResponse {
    private Boolean success;
    private Long paidAmount;
    private String paidAt;
}