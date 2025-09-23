package com.behcm.domain.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockPortfolioResponse {

    private BigDecimal totalMarketValue;
    private BigDecimal totalProfitLoss;
    private BigDecimal totalProfitLossRate;
    private List<StockHoldingDto> holdings;
    private String lastUpdated;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockHoldingDto {
        private String stockCode;
        private String stockName;
        private Integer quantity;
        private BigDecimal averagePrice;
        private BigDecimal currentPrice;
        private BigDecimal marketValue;
        private BigDecimal profitLoss;
        private BigDecimal profitLossRate;
        private String sector;
    }
}