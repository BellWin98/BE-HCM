package com.behcm.domain.workout.controller;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.workout.dto.WorkoutRequest;
import com.behcm.domain.workout.dto.WorkoutResponse;
import com.behcm.domain.workout.service.WorkoutLikeService;
import com.behcm.domain.workout.service.WorkoutService;
import com.behcm.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/workouts")
@RequiredArgsConstructor
@Tag(name = "Workout", description = "운동 인증 API")
public class WorkoutController {

    private final WorkoutService workoutService;
    private final WorkoutLikeService workoutLikeService;

    @PostMapping
    public ResponseEntity<ApiResponse<WorkoutResponse>> authenticateWorkout(
            @RequestParam String workoutDate,
            @RequestParam List<String> workoutTypes,
            @RequestParam Integer duration,
            @RequestParam List<MultipartFile> images,
            @AuthenticationPrincipal Member member
    ) {
        WorkoutRequest request = WorkoutRequest.builder()
                .workoutDate(workoutDate)
                .workoutTypes(workoutTypes)
                .duration(duration)
                .images(images)
                .build();

        WorkoutResponse response = workoutService.authenticateWorkout(member, request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{workoutId}/like")
    @Operation(summary = "운동 기록 좋아요", description = "특정 운동 기록에 좋아요를 추가합니다.")
    public ResponseEntity<ApiResponse<Void>> likeWorkout(
            @PathVariable Long workoutId,
            @AuthenticationPrincipal Member member
    ) {
        workoutLikeService.likeWorkout(member, workoutId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{workoutId}/like")
    @Operation(summary = "운동 기록 좋아요 취소", description = "특정 운동 기록의 좋아요를 취소합니다.")
    public ResponseEntity<ApiResponse<Void>> unlikeWorkout(
            @PathVariable Long workoutId,
            @AuthenticationPrincipal Member member
    ) {
        workoutLikeService.unlikeWorkout(member, workoutId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}