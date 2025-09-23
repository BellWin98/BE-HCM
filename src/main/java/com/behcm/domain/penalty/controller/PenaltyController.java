package com.behcm.domain.penalty.controller;

import com.behcm.domain.penalty.dto.*;
import com.behcm.domain.penalty.service.PenaltyService;
import com.behcm.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workout/rooms")
@RequiredArgsConstructor
public class PenaltyController {

    private final PenaltyService penaltyService;

    @GetMapping("{roomId}/penalty/account")
    public ResponseEntity<ApiResponse<BankAccountInfo>> getBankAccount(@PathVariable Long roomId) {
        BankAccountInfo response = penaltyService.getBankAccount(roomId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{roomId}/penalty/account")
    public ResponseEntity<ApiResponse<BankAccountInfo>> upsertBankAccount(@PathVariable Long roomId, @RequestBody BankAccountRequest request) {
        BankAccountInfo response = penaltyService.upsertBankAccount(roomId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{roomId}/penalty/unpaid/summary")
    public ResponseEntity<ApiResponse<PenaltyUnpaidSummary>> getUnpaidPenaltySummary(@PathVariable Long roomId) {
        PenaltyUnpaidSummary response = penaltyService.getUnpaidPenaltySummary(roomId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{roomId}/penalty/pay")
    public ResponseEntity<ApiResponse<PayPenaltyResponse>> payPenalty(@PathVariable Long roomId, @RequestBody PayPenaltyRequest request) {
        PayPenaltyResponse response = penaltyService.payPenalty(roomId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}