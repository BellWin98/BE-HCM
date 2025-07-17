package com.behcm.domain.workout.controller;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.workout.dto.WorkoutRequest;
import com.behcm.domain.workout.dto.WorkoutResponse;
import com.behcm.domain.workout.service.WorkoutService;
import com.behcm.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/workouts")
@RequiredArgsConstructor
public class WorkoutController {

    private final WorkoutService workoutService;

    @PostMapping
    public ResponseEntity<ApiResponse<WorkoutResponse>> authenticateWorkout(
            @RequestParam String workoutDate,
            @RequestParam String workoutType,
            @RequestParam Integer duration,
            @RequestParam MultipartFile image,
            @AuthenticationPrincipal Member member
    ) {
        WorkoutRequest request = WorkoutRequest.builder()
                .workoutDate(workoutDate)
                .workoutType(workoutType)
                .duration(duration)
                .image(image)
                .build();
        
        WorkoutResponse response = workoutService.authenticateWorkout(member, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}