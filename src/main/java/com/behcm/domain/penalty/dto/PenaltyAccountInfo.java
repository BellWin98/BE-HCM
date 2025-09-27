package com.behcm.domain.penalty.dto;

import com.behcm.domain.penalty.entity.PenaltyAccount;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PenaltyAccountInfo {
    private Long id;
    private String bankName;
    private String accountNumber;
    private String accountHolder;

    public static PenaltyAccountInfo from(PenaltyAccount penaltyAccount) {
        return PenaltyAccountInfo.builder()
                .id(penaltyAccount.getId())
                .bankName(penaltyAccount.getBankName())
                .accountNumber(penaltyAccount.getAccountNumber())
                .accountHolder(penaltyAccount.getAccountHolder())
                .build();
    }
}