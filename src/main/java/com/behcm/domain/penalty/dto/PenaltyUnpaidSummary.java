package com.behcm.domain.penalty.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PenaltyUnpaidSummary {
    private Long totalUnpaidAmount;
    private Integer unpaidCount;
    private List<PenaltyRecord> records;

    public static PenaltyUnpaidSummary from(List<PenaltyRecord> records) {
        Long totalAmount = records.stream()
                .mapToLong(PenaltyRecord::getPenaltyAmount)
                .sum();

        return PenaltyUnpaidSummary.builder()
                .totalUnpaidAmount(totalAmount)
                .unpaidCount(records.size())
                .records(records)
                .build();
    }
}