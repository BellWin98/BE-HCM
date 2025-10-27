package com.behcm.domain.member.controller;

import com.behcm.domain.member.dto.*;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.service.MemberService;
import com.behcm.domain.workout.dto.WorkoutFeedItemResponse;
import com.behcm.domain.workout.dto.WorkoutStatsResponse;
import com.behcm.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> getMyInfo(@AuthenticationPrincipal Member member) {
        MemberResponse response = MemberResponse.from(member);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/profile")
    @Operation(summary = "사용자 프로필 조회", description = "현재 로그인한 사용자의 프로필 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<MemberProfileResponse>> getMemberProfile(@AuthenticationPrincipal Member member) {
        MemberProfileResponse response = memberService.getMemberProfile(member);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/profile")
    @Operation(summary = "사용자 프로필 수정", description = "사용자의 프로필 정보를 수정합니다.")
    public ResponseEntity<ApiResponse<MemberProfileResponse>> updateMemberProfile(
            @AuthenticationPrincipal Member member,
            @Valid @RequestBody UpdateMemberProfileRequest request
    ) {
        MemberProfileResponse response = memberService.updateMemberProfile(member, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/workout-feed")
    @Operation(summary = "사용자 운동 피드 조회", description = "사용자의 운동 기록 피드를 조회합니다.")
    public ResponseEntity<ApiResponse<Page<WorkoutFeedItemResponse>>> getMemberWorkoutFeed(
            @AuthenticationPrincipal Member member,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<WorkoutFeedItemResponse> response = memberService.getMemberWorkoutFeed(member, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/workout-stats")
    @Operation(summary = "사용자 운동 통계 조회", description = "사용자의 운동 통계 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<WorkoutStatsResponse>> getMemberWorkoutStats(@AuthenticationPrincipal Member member) {
        WorkoutStatsResponse response = memberService.getMemberWorkoutStats(member);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/settings")
    @Operation(summary = "사용자 설정 조회", description = "사용자의 설정 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<MemberSettingsResponse>> getMemberSettings(@AuthenticationPrincipal Member member) {
        MemberSettingsResponse response = memberService.getMemberSettings(member);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/settings")
    @Operation(summary = "사용자 설정 수정", description = "사용자의 설정 정보를 수정합니다.")
    public ResponseEntity<ApiResponse<MemberSettingsResponse>> updateMemberSettings(
            @AuthenticationPrincipal Member member,
            @RequestBody UpdateMemberSettingsRequest request
    ) {
        MemberSettingsResponse response = memberService.updateMemberSettings(member, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

}
