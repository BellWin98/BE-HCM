package com.behcm.domain.penalty.controller;

import com.behcm.domain.penalty.dto.*;
import com.behcm.domain.penalty.service.PenaltyService;
import com.behcm.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/penalty/rooms")
@RequiredArgsConstructor
public class PenaltyController {

    private final PenaltyService penaltyService;

    @PostMapping("/{roomId}/account")
    public ResponseEntity<ApiResponse<PenaltyAccountInfo>> upsertPenaltyAccount(@PathVariable Long roomId, @RequestBody PenaltyAccountRequest request) {
        PenaltyAccountInfo response = penaltyService.upsertPenaltyAccount(roomId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("{roomId}/account")
    public ResponseEntity<ApiResponse<PenaltyAccountInfo>> getPenaltyAccount(@PathVariable Long roomId) {
        PenaltyAccountInfo response = penaltyService.getPenaltyAccount(roomId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{roomId}/account")
    public ResponseEntity<ApiResponse<Void>> deletePenaltyAccount(@PathVariable Long roomId) {
        penaltyService.deletePenaltyAccount(roomId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{roomId}/records")
    public ResponseEntity<ApiResponse<List<PenaltyRecord>>> getPenaltyRecords(@PathVariable Long roomId) {
        List<PenaltyRecord> response = penaltyService.getPenaltyRecords(roomId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}