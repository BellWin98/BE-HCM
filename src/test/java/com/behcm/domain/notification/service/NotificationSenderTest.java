package com.behcm.domain.notification.service;

import com.behcm.domain.knowledge.repository.KnowledgeAccessRepository;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.domain.notification.repository.FcmTokenRepository;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import com.behcm.domain.workout.repository.WorkoutRoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationSenderTest {

    @Mock
    private WorkoutRoomRepository workoutRoomRepository;
    @Mock
    private WorkoutRoomMemberRepository workoutRoomMemberRepository;
    @Mock
    private FcmTokenRepository fcmTokenRepository;
    @Mock
    private KnowledgeAccessRepository knowledgeAccessRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private FcmService fcmService;

    @InjectMocks
    private NotificationSender notificationSender;

    private void setId(Object entity, long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Member member(long id, String nickname) {
        Member m = Member.builder()
                .email(nickname + "@test.com")
                .nickname(nickname)
                .role(MemberRole.USER)
                .build();
        setId(m, id);
        return m;
    }

    private WorkoutRoom room(long id, Member owner) {
        WorkoutRoom room = WorkoutRoom.builder()
                .name("Test Room")
                .minWeeklyWorkouts(3)
                .penaltyEnabled(false)
                .maxMembers(10)
                .entryCode("ENTRY01")
                .owner(owner)
                .build();
        setId(room, id);
        return room;
    }

    @Test
    @DisplayName("sendToMember는 대상 회원의 토큰으로 발송한다")
    void sendToMember_sendsToTargetTokens() {
        given(fcmTokenRepository.findTokensByMemberIds(List.of(1L))).willReturn(List.of("t"));

        notificationSender.sendToMember(1L, "title", "body", "PENALTY_ASSIGNED", "/path");

        verify(fcmService).sendGroupNotification(1L, List.of("t"), "title", "body", "PENALTY_ASSIGNED-1", "/path");
    }

    @Test
    @DisplayName("sendToMember는 본문이 50자를 초과하면 잘라서 전달한다")
    void sendToMember_longBody_truncatesTo50Chars() {
        given(fcmTokenRepository.findTokensByMemberIds(List.of(1L))).willReturn(List.of("t"));

        notificationSender.sendToMember(1L, "title", "가".repeat(60), "PENALTY_ASSIGNED", "/path");

        verify(fcmService).sendGroupNotification(1L, List.of("t"), "title", "가".repeat(50) + "...", "PENALTY_ASSIGNED-1", "/path");
    }

    @Test
    @DisplayName("sendToRoom은 발신자를 제외한 방 멤버에게만 발송한다")
    void sendToRoom_excludesSender() {
        Member sender = member(1L, "sender");
        Member other = member(2L, "other");
        WorkoutRoom room = room(5L, sender);
        WorkoutRoomMember senderWrm = WorkoutRoomMember.builder().member(sender).workoutRoom(room).build();
        WorkoutRoomMember otherWrm = WorkoutRoomMember.builder().member(other).workoutRoom(room).build();
        given(workoutRoomRepository.findByIdAndIsActiveTrue(5L)).willReturn(Optional.of(room));
        given(workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAtFetchMember(room))
                .willReturn(List.of(senderWrm, otherWrm));
        given(fcmTokenRepository.findTokensByMemberIds(List.of(2L))).willReturn(List.of("other-token"));

        notificationSender.sendToRoom(5L, 1L, "title", "body", "CHAT", "/path");

        verify(fcmService).sendGroupNotification(1L, List.of("other-token"), "title", "body", "CHAT-5", "/path");
    }

    @Test
    @DisplayName("sendToRoom은 운동방이 없으면 예외 없이 발송하지 않는다")
    void sendToRoom_roomNotFound_doesNotThrowOrSend() {
        given(workoutRoomRepository.findByIdAndIsActiveTrue(5L)).willReturn(Optional.empty());

        assertThatCode(() -> notificationSender.sendToRoom(5L, 1L, "title", "body", "CHAT", "/path"))
                .doesNotThrowAnyException();

        verify(fcmService, never()).sendGroupNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("sendToKnowledgeReaders는 독자와 관리자를 합치고 중복과 작성자를 제거한다")
    @SuppressWarnings("unchecked")
    void sendToKnowledgeReaders_dedupesAndExcludesAuthor() {
        given(knowledgeAccessRepository.findReaderMemberIds()).willReturn(List.of(1L, 2L, 3L));
        given(memberRepository.findIdsByRole(MemberRole.ADMIN)).willReturn(List.of(3L, 4L));
        given(fcmTokenRepository.findTokensByMemberIds(any(Collection.class))).willReturn(List.of("t"));

        notificationSender.sendToKnowledgeReaders(3L, "title", "body", "KNOWLEDGE_NOTE", "/path");

        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(fcmTokenRepository).findTokensByMemberIds(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(1L, 2L, 4L);
        verify(fcmService).sendGroupNotification(3L, List.of("t"), "title", "body", "KNOWLEDGE_NOTE-3", "/path");
    }

    @Test
    @DisplayName("sendToKnowledgeReaders는 수신 대상이 없으면 토큰 조회도 발송도 하지 않는다")
    void sendToKnowledgeReaders_noRecipients_skipsTokenLookup() {
        given(knowledgeAccessRepository.findReaderMemberIds()).willReturn(List.of(3L));
        given(memberRepository.findIdsByRole(MemberRole.ADMIN)).willReturn(List.of());

        notificationSender.sendToKnowledgeReaders(3L, "title", "body", "KNOWLEDGE_NOTE", "/path");

        verify(fcmTokenRepository, never()).findTokensByMemberIds(any());
        verify(fcmService, never()).sendGroupNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("sendToKnowledgeReaders는 토큰이 없으면 발송하지 않는다")
    void sendToKnowledgeReaders_noTokens_doesNotSend() {
        given(knowledgeAccessRepository.findReaderMemberIds()).willReturn(List.of(1L));
        given(memberRepository.findIdsByRole(MemberRole.ADMIN)).willReturn(List.of());
        given(fcmTokenRepository.findTokensByMemberIds(any())).willReturn(List.of());

        notificationSender.sendToKnowledgeReaders(3L, "title", "body", "KNOWLEDGE_NOTE", "/path");

        verify(fcmService, never()).sendGroupNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("저장소 예외는 호출자에게 전파되지 않는다")
    void send_repositoryThrows_doesNotPropagate() {
        given(knowledgeAccessRepository.findReaderMemberIds()).willThrow(new RuntimeException("db down"));

        assertThatCode(() -> notificationSender.sendToKnowledgeReaders(3L, "title", "body", "KNOWLEDGE_NOTE", "/path"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("NotificationSender에는 Transactional이 없다")
    void noTransactionalAnnotation() {
        assertThat(NotificationSender.class.isAnnotationPresent(Transactional.class)).isFalse();
        for (Method method : NotificationSender.class.getDeclaredMethods()) {
            assertThat(method.isAnnotationPresent(Transactional.class))
                    .as(method.getName()).isFalse();
        }
    }
}
