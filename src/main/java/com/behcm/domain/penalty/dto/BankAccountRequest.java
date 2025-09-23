package com.behcm.domain.penalty.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BankAccountRequest {
    private String bankName;
    private String accountNumber;
    private String holderName;
}