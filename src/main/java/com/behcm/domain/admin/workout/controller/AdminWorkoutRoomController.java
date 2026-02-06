package com.behcm.domain.admin.workout.controller;

import com.behcm.domain.admin.workout.dto.AdminUpdateRoomRequest;
import com.behcm.domain.admin.workout.service.AdminWorkoutRoomService;
import com.behcm.domain.workout.dto.WorkoutRoomDetailResponse;
import com.behcm.domain.workout.dto.WorkoutRoomResponse;
import com.behcm.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/workout/rooms")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminWorkoutRoomController {

    private final AdminWorkoutRoomService adminWorkoutRoomService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<WorkoutRoomResponse>>> getRooms(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<WorkoutRoomResponse> result = adminWorkoutRoomService.getRooms(query, active, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<WorkoutRoomDetailResponse>> getRoomDetail(
            @PathVariable Long roomId
    ) {
        WorkoutRoomDetailResponse response = adminWorkoutRoomService.getRoomDetail(roomId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{roomId}")
    public ResponseEntity<ApiResponse<WorkoutRoomResponse>> updateRoomSettings(
            @PathVariable Long roomId,
            @Valid @RequestBody AdminUpdateRoomRequest request
    ) {
        WorkoutRoomResponse response = adminWorkoutRoomService.updateRoomSettings(roomId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

