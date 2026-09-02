package com.behcm.domain.social.controller;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.social.dto.CommentRequest;
import com.behcm.domain.social.dto.CommentResponse;
import com.behcm.domain.social.dto.ReactionMemberResponse;
import com.behcm.domain.social.dto.ReactionRequest;
import com.behcm.domain.social.dto.WorkoutSocialSummary;
import com.behcm.domain.social.service.WorkoutCommentService;
import com.behcm.domain.social.service.WorkoutReactionService;
import com.behcm.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workouts/{recordId}")
@RequiredArgsConstructor
@Tag(name = "Workout Social", description = "운동 인증 리액션/댓글 API")
public class WorkoutSocialController {

    private final WorkoutReactionService workoutReactionService;
    private final WorkoutCommentService workoutCommentService;

    @PostMapping("/reactions")
    @Operation(summary = "리액션 등록/변경", description = "운동 인증에 이모지 리액션을 남깁니다. 이미 남긴 경우 이모지가 교체됩니다.")
    public ResponseEntity<ApiResponse<WorkoutSocialSummary>> react(
            @PathVariable Long recordId,
            @Valid @RequestBody ReactionRequest request,
            @AuthenticationPrincipal Member member
    ) {
        WorkoutSocialSummary response = workoutReactionService.react(member, recordId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/reactions")
    @Operation(summary = "리액션 취소", description = "운동 인증에 남긴 내 리액션을 취소합니다.")
    public ResponseEntity<ApiResponse<WorkoutSocialSummary>> cancelReaction(
            @PathVariable Long recordId,
            @AuthenticationPrincipal Member member
    ) {
        WorkoutSocialSummary response = workoutReactionService.cancelReaction(member, recordId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/reactions")
    @Operation(summary = "리액션 상세 조회", description = "운동 인증에 누가 어떤 리액션을 남겼는지 조회합니다.")
    public ResponseEntity<ApiResponse<List<ReactionMemberResponse>>> getReactionMembers(
            @PathVariable Long recordId,
            @AuthenticationPrincipal Member member
    ) {
        List<ReactionMemberResponse> response = workoutReactionService.getReactionMembers(member, recordId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/comments")
    @Operation(summary = "댓글 목록 조회", description = "운동 인증에 달린 댓글을 오래된 순으로 조회합니다.")
    public ResponseEntity<ApiResponse<Page<CommentResponse>>> getComments(
            @PathVariable Long recordId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Member member
    ) {
        Page<CommentResponse> response = workoutCommentService.getComments(member, recordId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/comments")
    @Operation(summary = "댓글 작성", description = "운동 인증에 댓글을 작성합니다. 인증 작성자에게 푸시가 발송됩니다.")
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
            @PathVariable Long recordId,
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal Member member
    ) {
        CommentResponse response = workoutCommentService.addComment(member, recordId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "댓글 삭제", description = "본인이 작성한 댓글을 삭제합니다.")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable Long recordId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal Member member
    ) {
        workoutCommentService.deleteComment(member, recordId, commentId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
