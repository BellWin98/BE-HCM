package com.behcm.domain.workout.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.workout.dto.CommentResponse;
import com.behcm.domain.workout.entity.WorkoutComment;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.repository.WorkoutCommentRepository;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkoutCommentService {

    private final WorkoutCommentRepository workoutCommentRepository;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final WorkoutRoomService workoutRoomService;

    public List<CommentResponse> getComments(Long workoutId, Member currentMember) {
        WorkoutRecord workoutRecord = workoutRecordRepository.findById(workoutId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_RECORD_NOT_FOUND));

        // 같은 방 멤버인지 확인
        if (!workoutRoomService.areMembersInSameRoom(currentMember, workoutRecord.getMember())) {
            throw new CustomException(ErrorCode.NOT_SAME_ROOM_MEMBER);
        }

        List<WorkoutComment> comments = workoutCommentRepository.findByWorkoutRecordIdOrderByCreatedAtAsc(workoutId);
        return comments.stream()
                .map(CommentResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public CommentResponse createComment(Long workoutId, String content, Member member) {
        // 댓글 내용 검증
        if (content == null || content.trim().isEmpty()) {
            throw new CustomException(ErrorCode.COMMENT_CONTENT_REQUIRED);
        }
        if (content.length() > 500) {
            throw new CustomException(ErrorCode.COMMENT_TOO_LONG);
        }

        WorkoutRecord workoutRecord = workoutRecordRepository.findById(workoutId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_RECORD_NOT_FOUND));

        // 같은 방 멤버인지 확인
        if (!workoutRoomService.areMembersInSameRoom(member, workoutRecord.getMember())) {
            throw new CustomException(ErrorCode.NOT_SAME_ROOM_MEMBER);
        }

        WorkoutComment comment = WorkoutComment.builder()
                .workoutRecord(workoutRecord)
                .member(member)
                .content(content.trim())
                .build();

        WorkoutComment savedComment = workoutCommentRepository.save(comment);
        return CommentResponse.from(savedComment);
    }

    @Transactional
    public void deleteComment(Long commentId, Member member) {
        WorkoutComment comment = workoutCommentRepository.findByIdAndMemberId(commentId, member.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));

        // 작성자 본인인지 확인 (이미 findByIdAndMemberId로 필터링되었지만 명시적으로 확인)
        if (!comment.getMember().getId().equals(member.getId())) {
            throw new CustomException(ErrorCode.NOT_COMMENT_OWNER);
        }

        workoutCommentRepository.delete(comment);
    }
}
