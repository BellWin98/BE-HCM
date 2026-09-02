package com.behcm.domain.social.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.notification.service.NotificationFacade;
import com.behcm.domain.social.dto.CommentRequest;
import com.behcm.domain.social.dto.CommentResponse;
import com.behcm.domain.social.entity.WorkoutComment;
import com.behcm.domain.social.repository.WorkoutCommentRepository;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkoutCommentService {

    private static final String COMMENT_TYPE = "WORKOUT_COMMENT";

    private final WorkoutCommentRepository workoutCommentRepository;
    private final WorkoutRecordAccessGuard workoutRecordAccessGuard;
    private final NotificationFacade notificationFacade;

    public CommentResponse addComment(Member member, Long recordId, CommentRequest request) {
        WorkoutRecord workoutRecord = workoutRecordAccessGuard.getAccessibleRecord(member, recordId);

        WorkoutComment comment = workoutCommentRepository.save(WorkoutComment.builder()
                .workoutRecord(workoutRecord)
                .member(member)
                .content(request.getContent())
                .build());

        notifyRecordOwner(member, workoutRecord, request.getContent());

        return CommentResponse.of(comment, member);
    }

    public void deleteComment(Member member, Long recordId, Long commentId) {
        WorkoutRecord workoutRecord = workoutRecordAccessGuard.getAccessibleRecord(member, recordId);

        WorkoutComment comment = workoutCommentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));
        // 다른 인증의 댓글 id 로 들어오면 "없는 댓글"로 취급한다. 존재 여부를 흘리지 않기 위함이다.
        if (!Objects.equals(comment.getWorkoutRecord().getId(), workoutRecord.getId())) {
            throw new CustomException(ErrorCode.COMMENT_NOT_FOUND);
        }
        if (!comment.isWrittenBy(member)) {
            throw new CustomException(ErrorCode.NOT_COMMENT_AUTHOR);
        }

        workoutCommentRepository.delete(comment);
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> getComments(Member member, Long recordId, int page, int size) {
        WorkoutRecord workoutRecord = workoutRecordAccessGuard.getAccessibleRecord(member, recordId);
        Pageable pageable = PageRequest.of(page, size);

        return workoutCommentRepository.findAllByWorkoutRecordFetchMember(workoutRecord, pageable)
                .map(comment -> CommentResponse.of(comment, member));
    }

    // 리액션은 빈도가 높아 알림 피로를 유발하므로 댓글에만 푸시를 보낸다.
    private void notifyRecordOwner(Member author, WorkoutRecord workoutRecord, String content) {
        Member owner = workoutRecord.getMember();
        if (Objects.equals(owner.getId(), author.getId())) {
            return;
        }
        String title = String.format("%s님이 회원님의 %s 운동 인증에 댓글을 남겼어요!", author.getNickname(), workoutRecord.getWorkoutDate());
        notificationFacade.notifyMember(owner, title, content, COMMENT_TYPE, "");
    }
}
