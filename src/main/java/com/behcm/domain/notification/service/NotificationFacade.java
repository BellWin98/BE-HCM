package com.behcm.domain.notification.service;

import com.behcm.domain.member.entity.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 알림 발송 진입점. 발송은 트랜잭션 커밋 이후에 비동기({@link NotificationSender})로 예약하며,
 * 예약 실패가 호출한 비즈니스 로직의 성공/롤백에 영향을 주지 않도록 예외를 삼키고 로그만 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationFacade {

    private final NotificationSender notificationSender;
    private final FcmService fcmService;

    @Transactional
    public void registerFcmToken(Member member, String token) {
        fcmService.saveFcmToken(member, token);
        log.debug("FCM token registered (memberId={})", member.getId());
    }

    public void notifyMember(Member targetMember, String title, String body, String type, String path) {
        Long targetId = targetMember.getId();
        runAfterCommit(() -> notificationSender.sendToMember(targetId, title, body, type, path));
    }

    public void notifyRoomMembers(Long roomId, Member sender, String title, String body, String type, String path) {
        Long senderId = sender.getId();
        runAfterCommit(() -> notificationSender.sendToRoom(roomId, senderId, title, body, type, path));
    }

    public void notifyKnowledgeReaders(Member author, String title, String body, String type, String path) {
        Long authorId = author.getId();
        runAfterCommit(() -> notificationSender.sendToKnowledgeReaders(authorId, title, body, type, path));
    }

    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runSafely(task);
                }
            });
        } else {
            runSafely(task);
        }
    }

    private void runSafely(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.warn("Notification scheduling failed", e);
        }
    }
}
