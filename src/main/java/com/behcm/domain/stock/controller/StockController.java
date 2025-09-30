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

    @GetMapping("/price/{stockCode}")
    public ResponseEntity<ApiResponse<StockPriceResponse>> getStockPrice(
            @PathVariable String stockCode
    ) {
        StockPriceResponse stockPrice = stockService.getStockPrice(stockCode);
        return ResponseEntity.ok(ApiResponse.success(stockPrice));
    }

    @GetMapping("/info/{stockCode}")
    public ResponseEntity<ApiResponse<StockInfoResponse>> getStockInfo(
            @PathVariable String stockCode
    ) {
        StockInfoResponse stockInfo = stockService.getStockInfo(stockCode);
        return ResponseEntity.ok(ApiResponse.success(stockInfo));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refreshStockData() {
        stockService.refreshStockData();
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/trading-profit-loss")
    public ResponseEntity<ApiResponse<TradingProfitLossResponse>> getTradingProfitLoss(
            @RequestBody TradingProfitLossRequest request
    ) {
        TradingProfitLossResponse profitLoss = stockService.getTradingProfitLoss(request);
        return ResponseEntity.ok(ApiResponse.success(profitLoss));
    }
}