package com.behcm.domain.workout.controller;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.workout.dto.CommentRequest;
import com.behcm.domain.workout.dto.CommentResponse;
import com.behcm.domain.workout.service.WorkoutCommentService;
import com.behcm.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workouts")
@RequiredArgsConstructor
@Tag(name = "Workout Comment", description = "운동 기록 댓글 API")
public class WorkoutCommentController {

    private final WorkoutCommentService workoutCommentService;

    @GetMapping("/{workoutId}/comments")
    @Operation(summary = "댓글 조회", description = "특정 운동 기록의 댓글 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> getComments(
            @PathVariable Long workoutId,
            @AuthenticationPrincipal Member member
    ) {
        List<CommentResponse> comments = workoutCommentService.getComments(workoutId, member);
        return ResponseEntity.ok(ApiResponse.success(comments));
    }

    @PostMapping("/{workoutId}/comments")
    @Operation(summary = "댓글 작성", description = "특정 운동 기록에 댓글을 작성합니다.")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @PathVariable Long workoutId,
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal Member member
    ) {
        CommentResponse response = workoutCommentService.createComment(workoutId, request.getContent(), member);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "댓글 삭제", description = "댓글을 삭제합니다. 본인 댓글만 삭제 가능합니다.")
    public ResponseEntity<ApiResponse<String>> deleteComment(
            @PathVariable Long commentId,
            @AuthenticationPrincipal Member member
    ) {
        workoutCommentService.deleteComment(commentId, member);
        return ResponseEntity.ok(ApiResponse.success(null, "댓글이 삭제되었습니다."));
    }
}
