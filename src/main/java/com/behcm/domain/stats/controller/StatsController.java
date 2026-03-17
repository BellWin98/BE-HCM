package com.behcm.domain.stats.controller;

import com.behcm.domain.stats.dto.LandingStatsResponse;
import com.behcm.domain.stats.service.StatsService;
import com.behcm.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/landing-summary")
    @Operation(summary = "Landing 페이지 통계 조회", description = "랜딩 페이지 SocialProofSection 에서 사용하는 통계 데이터를 조회합니다.")
    public ResponseEntity<ApiResponse<LandingStatsResponse>> getLandingSummary() {
        LandingStatsResponse response = statsService.getLandingStats();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

