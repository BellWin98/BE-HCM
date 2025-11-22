package com.behcm.domain.stock.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class TradingProfitLossResponse {
    private String period;
    private BigDecimal totalBuyAmount;
    private BigDecimal totalSellAmount;
    private BigDecimal totalProfitLoss;
    private BigDecimal totalProfitLossRate;
    private BigDecimal totalFee;
    private BigDecimal totalTax;
    private Integer tradeCount;
    private List<TradingProfitLossDto> trades;

    @Getter
    @Builder
    public static class TradingProfitLossDto {
        private String stockCode;
        private String stockName;
        private String tradeDate;
        private String tradeType;
        private Integer quantity;
        private BigDecimal price;
        private BigDecimal amount;
        private BigDecimal profitLoss;
        private BigDecimal profitLossRate;
        private BigDecimal fee;
        private BigDecimal tax;
    }
}