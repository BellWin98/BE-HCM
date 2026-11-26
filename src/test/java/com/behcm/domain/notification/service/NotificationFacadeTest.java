package com.behcm.domain.notification.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationFacadeTest {

    @Mock
    private NotificationSender notificationSender;

    @Mock
    private FcmService fcmService;

    @InjectMocks
    private NotificationFacade notificationFacade;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Member member(long id, String nickname) {
        Member m = Member.builder()
                .email(nickname + "@test.com")
                .nickname(nickname)
                .role(MemberRole.USER)
                .build();
        try {
            Field field = m.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(m, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return m;
    }

    private List<TransactionSynchronization> synchronizations() {
        return TransactionSynchronizationManager.getSynchronizations();
    }

    @Test
    @DisplayName("registerFcmToken은 FcmService에 토큰 저장을 위임한다")
    void registerFcmToken_delegatesToFcmService() {
        Member member = member(1L, "user");

        notificationFacade.registerFcmToken(member, "fcm-token");

        verify(fcmService).saveFcmToken(member, "fcm-token");
    }

    @Test
    @DisplayName("트랜잭션이 없으면 즉시 Sender에 위임한다")
    void noTransaction_sendsImmediately() {
        notificationFacade.notifyMember(member(1L, "target"), "title", "body", "PENALTY_ASSIGNED", "/path");

        verify(notificationSender).sendToMember(1L, "title", "body", "PENALTY_ASSIGNED", "/path");
    }

    @Test
    @DisplayName("트랜잭션 중이면 커밋 후에야 Sender에 위임한다")
    void inTransaction_defersUntilAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        notificationFacade.notifyRoomMembers(5L, member(1L, "sender"), "title", "body", "CHAT", "/path");

        verifyNoInteractions(notificationSender);
        synchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(notificationSender).sendToRoom(5L, 1L, "title", "body", "CHAT", "/path");
    }

    @Test
    @DisplayName("롤백되면 발송하지 않는다")
    void rollback_doesNotSend() {
        TransactionSynchronizationManager.initSynchronization();

        notificationFacade.notifyMember(member(1L, "target"), "title", "body", "PENALTY_ASSIGNED", "/path");
        synchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(notificationSender);
    }

    @Test
    @DisplayName("커밋 후 발송 예약이 실패해도 예외를 던지지 않는다")
    void afterCommit_submitFails_doesNotThrow() {
        willThrow(new TaskRejectedException("queue full"))
                .given(notificationSender).sendToKnowledgeReaders(7L, "title", "body", "KNOWLEDGE_NOTE", "/path");
        TransactionSynchronizationManager.initSynchronization();

        notificationFacade.notifyKnowledgeReaders(member(7L, "author"), "title", "body", "KNOWLEDGE_NOTE", "/path");

        assertThatCode(() -> synchronizations().forEach(TransactionSynchronization::afterCommit))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("notifyKnowledgeReaders는 작성자 id를 Sender에 전달한다")
    void notifyKnowledgeReaders_passesAuthorId() {
        notificationFacade.notifyKnowledgeReaders(member(7L, "author"), "title", "body", "KNOWLEDGE_NOTE", "/path");

        verify(notificationSender).sendToKnowledgeReaders(7L, "title", "body", "KNOWLEDGE_NOTE", "/path");
    }
}
