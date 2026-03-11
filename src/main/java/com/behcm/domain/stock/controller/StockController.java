package com.behcm.domain.stock.controller;

import com.behcm.domain.stock.dto.*;
import com.behcm.domain.stock.service.StockService;
import com.behcm.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @GetMapping("/portfolio")
    public ResponseEntity<ApiResponse<StockPortfolioResponse>> getStockPortfolio() {
        StockPortfolioResponse portfolio = stockService.getStockPortfolio();
        return ResponseEntity.ok(ApiResponse.success(portfolio));
    }

    @PostMapping("/trading-profit-loss")
    public ResponseEntity<ApiResponse<TradingProfitLossResponse>> getTradingProfitLoss(
            @RequestBody TradingProfitLossRequest request
    ) {
        TradingProfitLossResponse profitLoss = stockService.getTradingProfitLoss(request);
        return ResponseEntity.ok(ApiResponse.success(profitLoss));
    }
}