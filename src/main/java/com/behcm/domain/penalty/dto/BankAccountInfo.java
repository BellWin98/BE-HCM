package com.behcm.domain.penalty.dto;

import com.behcm.domain.penalty.entity.BankAccount;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BankAccountInfo {
    private Long id;
    private String bankName;
    private String accountNumber;
    private String holderName;

    public static BankAccountInfo from(BankAccount bankAccount) {
        return BankAccountInfo.builder()
                .id(bankAccount.getId())
                .bankName(bankAccount.getBankName())
                .accountNumber(bankAccount.getAccountNumber())
                .holderName(bankAccount.getHolderName())
                .build();
    }
}