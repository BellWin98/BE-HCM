package com.behcm.domain.workout.controller;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.workout.dto.CreateWorkoutRoomRequest;
import com.behcm.domain.workout.dto.WorkoutRoomDetailResponse;
import com.behcm.domain.workout.dto.WorkoutRoomResponse;
import com.behcm.domain.workout.service.WorkoutRoomService;
import com.behcm.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workout/rooms")
@RequiredArgsConstructor
public class WorkoutRoomController {

    private final WorkoutRoomService workoutRoomService;

    @PostMapping
    public ResponseEntity<ApiResponse<WorkoutRoomResponse>> createWorkoutRoom(
            @Valid @RequestBody CreateWorkoutRoomRequest request,
            @AuthenticationPrincipal Member member
    ) {
        WorkoutRoomResponse response = workoutRoomService.createWorkoutRoom(member, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<WorkoutRoomDetailResponse>> getCurrentWorkoutRoom(
            @AuthenticationPrincipal Member member
    ) {
        WorkoutRoomDetailResponse response = workoutRoomService.getCurrentWorkoutRoom(member);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/validate")
    public ResponseEntity<Boolean> isMemberInWorkoutRoom(
            @AuthenticationPrincipal Member member
    ) {
        boolean response = workoutRoomService.isMemberInWorkoutRoom(member);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkoutRoomResponse>>> getWorkoutRooms() {
        List<WorkoutRoomResponse> response = workoutRoomService.getWorkoutRooms();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/join/{workoutRoomId}")
    public ResponseEntity<ApiResponse<WorkoutRoomResponse>> joinWorkoutRoom(
            @PathVariable Long workoutRoomId,
            @RequestParam String entryCode,
            @AuthenticationPrincipal Member member
    ) {
        WorkoutRoomResponse response = workoutRoomService.joinWorkoutRoom(workoutRoomId, entryCode, member);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
