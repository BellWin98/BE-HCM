package com.behcm.domain.stock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TradingProfitLossRequest {

    @NotBlank(message = "조회 시작일은 필수입니다.")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "조회 시작일은 yyyy-MM-dd 형식이어야 합니다.")
    private String startDate;

    @NotBlank(message = "조회 종료일은 필수입니다.")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "조회 종료일은 yyyy-MM-dd 형식이어야 합니다.")
    private String endDate;

    @NotBlank(message = "조회 기간 유형은 필수입니다.")
    private String periodType;
}
