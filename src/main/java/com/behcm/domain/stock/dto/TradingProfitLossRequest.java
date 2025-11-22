package com.behcm.domain.stock.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TradingProfitLossRequest {
    private String startDate;
    private String endDate;
    private String periodType;
}