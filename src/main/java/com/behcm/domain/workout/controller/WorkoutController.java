package com.behcm.domain.workout.controller;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.workout.dto.WorkoutRequest;
import com.behcm.domain.workout.dto.WorkoutResponse;
import com.behcm.domain.workout.service.WorkoutService;
import com.behcm.global.common.ApiResponse;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
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

    @PostMapping
    public ResponseEntity<ApiResponse<WorkoutResponse>> authenticateWorkout(
            @RequestParam String workoutDate,
            @RequestParam List<String> workoutTypes,
            @RequestParam Integer duration,
            @RequestParam List<MultipartFile> images,
            @AuthenticationPrincipal Member member
    ) {
        if (images.size() > 3) {
            throw new CustomException(ErrorCode.COUNT_LIMIT_EXCEEDED, "업로드 가능한 파일 개수를 초과했습니다.");
        }
        if (workoutTypes.size() > 3) {
            throw new CustomException(ErrorCode.COUNT_LIMIT_EXCEEDED, "운동 종류는 최대 3개까지 등록할 수 있습니다.");
        }
        WorkoutRequest request = WorkoutRequest.builder()
                .workoutDate(workoutDate)
                .workoutTypes(workoutTypes)
                .duration(duration)
                .images(images)
                .build();
        WorkoutResponse response = workoutService.authenticateWorkout(member, request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}