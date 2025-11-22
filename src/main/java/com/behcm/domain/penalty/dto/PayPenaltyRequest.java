package com.behcm.domain.penalty.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PayPenaltyRequest {
    private Long amount;
    private List<Long> penaltyRecordIds;
}