package com.behcm.domain.penalty.dto;

import lombok.Data;

@Data
public class PenaltyAccountRequest {
    private String bankName;
    private String accountNumber;
    private String accountHolder;
}