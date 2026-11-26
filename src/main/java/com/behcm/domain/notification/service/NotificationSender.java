package com.behcm.domain.notification.service;

import com.behcm.domain.knowledge.repository.KnowledgeAccessRepository;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.domain.notification.repository.FcmTokenRepository;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import com.behcm.domain.workout.repository.WorkoutRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 알림 발송기. 별도 스레드(notificationExecutor)에서 돌며, 트랜잭션을 열지 않고(각 조회는 저장소 단위로 끝난다),
 * 어떤 실패도 호출자에게 전파하지 않는다. 의도적으로 @Transactional 을 붙이지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSender {

    private final WorkoutRoomRepository workoutRoomRepository;
    private final WorkoutRoomMemberRepository workoutRoomMemberRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final KnowledgeAccessRepository knowledgeAccessRepository;
    private final MemberRepository memberRepository;
    private final FcmService fcmService;

    @Async("notificationExecutor")
    public void sendToMember(Long targetId, String title, String body, String type, String path) {
        try {
            send(targetId, Set.of(targetId), title, body, type + "-" + targetId, path);
        } catch (Exception e) {
            log.warn("Notification send failed (type={}, targetId={})", type, targetId, e);
        }
    }

    @Async("notificationExecutor")
    public void sendToRoom(Long roomId, Long senderId, String title, String body, String type, String path) {
        try {
            Optional<WorkoutRoom> room = workoutRoomRepository.findByIdAndIsActiveTrue(roomId);
            if (room.isEmpty()) {
                log.warn("Notification skipped: workout room not found (type={}, roomId={})", type, roomId);
                return;
            }
            Set<Long> recipientIds = new HashSet<>();
            for (WorkoutRoomMember wrm : workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAtFetchMember(room.get())) {
                recipientIds.add(wrm.getMember().getId());
            }
            recipientIds.remove(senderId);
            send(senderId, recipientIds, title, body, type + "-" + roomId, path);
        } catch (Exception e) {
            log.warn("Notification send failed (type={}, roomId={})", type, roomId, e);
        }
    }

    @Async("notificationExecutor")
    public void sendToKnowledgeReaders(Long authorId, String title, String body, String type, String path) {
        try {
            Set<Long> recipientIds = new HashSet<>(knowledgeAccessRepository.findReaderMemberIds());
            recipientIds.addAll(memberRepository.findIdsByRole(MemberRole.ADMIN));
            recipientIds.remove(authorId);
            send(authorId, recipientIds, title, body, type + "-" + authorId, path);
        } catch (Exception e) {
            log.warn("Notification send failed (type={}, authorId={})", type, authorId, e);
        }
    }

    private void send(Long senderId, Set<Long> recipientIds, String title, String body, String tag, String path) {
        if (recipientIds.isEmpty()) {
            return;
        }
        List<String> tokens = fcmTokenRepository.findTokensByMemberIds(List.copyOf(recipientIds));
        if (tokens.isEmpty()) {
            return;
        }
        fcmService.sendGroupNotification(senderId, tokens, title, truncateMessage(body), tag, path);
    }

    /**
     * 메시지 길이 제한 (너무 긴 메시지는 생략)
     */
    private String truncateMessage(String message) {
        int maxLength = 50;
        if (message != null && message.codePointCount(0, message.length()) > maxLength) {
            return message.substring(0, message.offsetByCodePoints(0, maxLength)) + "...";
        }
        return message;
    }
}
