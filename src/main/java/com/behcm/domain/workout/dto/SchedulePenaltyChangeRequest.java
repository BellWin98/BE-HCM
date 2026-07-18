package com.behcm.domain.workout.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SchedulePenaltyChangeRequest {

    @NotNull(message = "벌금제도 사용 여부는 필수입니다.")
    private Boolean penaltyEnabled;

    @Min(value = 1000, message = "회당 벌금은 1000원 이상이어야 합니다.")
    private Long penaltyPerMiss;

    @NotNull(message = "전환 예정일은 필수입니다.")
    private LocalDate effectiveDate;

    @AssertTrue(message = "벌금제도를 켜는 경우 회당 벌금은 필수입니다.")
    public boolean isPenaltyAmountValidWhenEnabled() {
        return penaltyEnabled == null || !penaltyEnabled || penaltyPerMiss != null;
    }
}
