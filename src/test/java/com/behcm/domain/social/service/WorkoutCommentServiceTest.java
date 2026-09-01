package com.behcm.domain.social.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.notification.service.NotificationFacade;
import com.behcm.domain.social.dto.CommentRequest;
import com.behcm.domain.social.dto.CommentResponse;
import com.behcm.domain.social.entity.WorkoutComment;
import com.behcm.domain.social.repository.WorkoutCommentRepository;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WorkoutCommentServiceTest {

    @Mock
    private WorkoutCommentRepository workoutCommentRepository;

    @Mock
    private WorkoutRecordAccessGuard workoutRecordAccessGuard;

    @Mock
    private NotificationFacade notificationFacade;

    @InjectMocks
    private WorkoutCommentService workoutCommentService;

    private Member member(long id, String nickname) {
        Member member = Member.builder()
                .email(nickname + "@test.com")
                .nickname(nickname)
                .role(MemberRole.USER)
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private WorkoutRecord record(Member owner) {
        WorkoutRoom room = WorkoutRoom.builder()
                .name("Test Room")
                .minWeeklyWorkouts(3)
                .penaltyEnabled(false)
                .maxMembers(10)
                .entryCode("ENTRY01")
                .owner(owner)
                .build();
        WorkoutRecord record = WorkoutRecord.builder()
                .member(owner)
                .workoutRoom(room)
                .workoutDate(LocalDate.now())
                .duration(30)
                .build();
        ReflectionTestUtils.setField(record, "id", 10L);
        return record;
    }

    private CommentRequest request(String content) {
        CommentRequest request = new CommentRequest();
        request.setContent(content);
        return request;
    }

    private WorkoutComment comment(WorkoutRecord record, Member author, String content) {
        WorkoutComment comment = WorkoutComment.builder()
                .workoutRecord(record)
                .member(author)
                .content(content)
                .build();
        ReflectionTestUtils.setField(comment, "id", 100L);
        return comment;
    }

    @Test
    @DisplayName("addComment는 댓글을 저장하고 인증 작성자에게 푸시를 보낸다")
    void addComment_savesAndNotifiesRecordOwner() {
        Member owner = member(1L, "owner");
        Member commenter = member(2L, "commenter");
        WorkoutRecord record = record(owner);
        given(workoutRecordAccessGuard.getAccessibleRecord(commenter, 10L)).willReturn(record);
        given(workoutCommentRepository.save(any(WorkoutComment.class))).willAnswer(invocation -> invocation.getArgument(0));

        CommentResponse response = workoutCommentService.addComment(commenter, 10L, request("오늘도 화이팅!"));

        ArgumentCaptor<WorkoutComment> captor = ArgumentCaptor.forClass(WorkoutComment.class);
        verify(workoutCommentRepository).save(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("오늘도 화이팅!");
        assertThat(response.getNickname()).isEqualTo("commenter");
        assertThat(response.isMine()).isTrue();
        verify(notificationFacade).notifyMember(eq(owner), contains("commenter"), eq("오늘도 화이팅!"), anyString(), anyString());
    }

    @Test
    @DisplayName("addComment는 본인 인증에 단 댓글이면 푸시를 보내지 않는다")
    void addComment_onOwnRecord_doesNotNotify() {
        Member owner = member(1L, "owner");
        WorkoutRecord record = record(owner);
        given(workoutRecordAccessGuard.getAccessibleRecord(owner, 10L)).willReturn(record);
        given(workoutCommentRepository.save(any(WorkoutComment.class))).willAnswer(invocation -> invocation.getArgument(0));

        workoutCommentService.addComment(owner, 10L, request("자축"));

        verifyNoInteractions(notificationFacade);
    }

    @Test
    @DisplayName("deleteComment는 작성자 본인의 댓글을 삭제한다")
    void deleteComment_byAuthor_deletes() {
        Member commenter = member(2L, "commenter");
        WorkoutRecord record = record(member(1L, "owner"));
        WorkoutComment comment = comment(record, commenter, "화이팅");
        given(workoutRecordAccessGuard.getAccessibleRecord(commenter, 10L)).willReturn(record);
        given(workoutCommentRepository.findById(100L)).willReturn(Optional.of(comment));

        workoutCommentService.deleteComment(commenter, 10L, 100L);

        verify(workoutCommentRepository).delete(comment);
    }

    @Test
    @DisplayName("deleteComment는 작성자가 아니면 예외를 던진다")
    void deleteComment_byOtherMember_throws() {
        Member commenter = member(2L, "commenter");
        Member intruder = member(3L, "intruder");
        WorkoutRecord record = record(member(1L, "owner"));
        WorkoutComment comment = comment(record, commenter, "화이팅");
        given(workoutRecordAccessGuard.getAccessibleRecord(intruder, 10L)).willReturn(record);
        given(workoutCommentRepository.findById(100L)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> workoutCommentService.deleteComment(intruder, 10L, 100L))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.NOT_COMMENT_AUTHOR.getMessage());

        verify(workoutCommentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteComment는 다른 운동 기록의 댓글 id면 예외를 던진다")
    void deleteComment_commentOfAnotherRecord_throws() {
        Member commenter = member(2L, "commenter");
        WorkoutRecord record = record(member(1L, "owner"));
        WorkoutRecord otherRecord = record(member(1L, "owner"));
        ReflectionTestUtils.setField(otherRecord, "id", 11L);
        WorkoutComment comment = comment(otherRecord, commenter, "화이팅");
        given(workoutRecordAccessGuard.getAccessibleRecord(commenter, 10L)).willReturn(record);
        given(workoutCommentRepository.findById(100L)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> workoutCommentService.deleteComment(commenter, 10L, 100L))
                .isInstanceOf(CustomException.class)
                .hasMessage(ErrorCode.COMMENT_NOT_FOUND.getMessage());

        verify(workoutCommentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("getComments는 조회 주체 기준으로 mine 플래그를 채운다")
    void getComments_marksOwnComments() {
        Member owner = member(1L, "owner");
        Member commenter = member(2L, "commenter");
        WorkoutRecord record = record(owner);
        Pageable pageable = PageRequest.of(0, 20);
        Page<WorkoutComment> page = new PageImpl<>(List.of(comment(record, commenter, "화이팅")), pageable, 1);
        given(workoutRecordAccessGuard.getAccessibleRecord(owner, 10L)).willReturn(record);
        given(workoutCommentRepository.findAllByWorkoutRecordFetchMember(record, pageable)).willReturn(page);

        Page<CommentResponse> result = workoutCommentService.getComments(owner, 10L, 0, 20);

        assertThat(result.getContent()).singleElement().satisfies(response -> {
            assertThat(response.getNickname()).isEqualTo("commenter");
            assertThat(response.isMine()).isFalse();
        });
    }
}
