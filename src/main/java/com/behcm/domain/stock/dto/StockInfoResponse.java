package com.behcm.domain.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockInfoResponse {

    private String stockCode;
    private String stockName;
    private String marketType;
    private String sector;
    private BigDecimal marketCap;
    private BigDecimal per;
    private BigDecimal pbr;
    private BigDecimal eps;
    private BigDecimal bps;
    private String listedDate;
    private Long listedShares;
    private String description;
}