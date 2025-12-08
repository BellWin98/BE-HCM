package com.behcm.domain.notification.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.notification.dto.FcmTokenRequest;
import com.behcm.domain.notification.dto.NotifyChatRequest;
import com.behcm.domain.notification.dto.NotifyWorkoutRequest;
import com.behcm.domain.notification.entity.FcmToken;
import com.behcm.domain.notification.repository.FcmTokenRepository;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.repository.WorkoutRoomRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final FcmTokenRepository fcmTokenRepository;
    private final WorkoutRoomRepository workoutRoomRepository;
    private final FcmService fcmService;

    /**
     * FCM 토큰 등록/갱신
     */
    @Transactional
    public void registerFcmToken(Member member, FcmTokenRequest request) {
        // 기존에 같은 토큰이 있는지 확인
        fcmTokenRepository.findByToken(request.getToken())
                .ifPresentOrElse(
                        existingToken -> {
                            // 다른 사용자의 토큰이면 비활성화
                            if (!existingToken.getMember().getId().equals(member.getId())) {
                                existingToken.deactivate();
                            }
                        },
                        () -> {
                            // 해당 멤버의 기존 활성 토큰 비활성화
                            fcmTokenRepository.findByMemberAndIsActiveTrue(member)
                                    .ifPresent(FcmToken::deactivate);
                        }
                );

        // 새 토큰 저장
        FcmToken fcmToken = FcmToken.builder()
                .member(member)
                .token(request.getToken())
                .build();

        fcmTokenRepository.save(fcmToken);
        log.info("FCM token registered for member: {}", member.getId());
    }

    /**
     * 운동 인증 알림 전송
     */
    @Transactional
    public void notifyWorkout(Member sender, Long roomId, NotifyWorkoutRequest request) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND));

        // 운동방의 다른 멤버들에게 알림 전송 (본인 제외)
        List<FcmToken> fcmTokens = fcmTokenRepository.findActiveTokensByRoomIdExcludingMember(
                roomId, sender.getId()
        );

        if (fcmTokens.isEmpty()) {
            log.info("No active FCM tokens found for room: {}", roomId);
            return;
        }

        // 알림 제목과 내용 생성
        String title = workoutRoom.getName();
        String body = String.format("%s님이 %d분 운동을 완료했어요!", sender.getNickname(), request.getDuration());

        // 추가 데이터
        Map<String, String> data = new HashMap<>();
        data.put("type", "workout");
        data.put("roomId", roomId.toString());
        data.put("senderNickname", sender.getNickname());
        data.put("workoutDate", request.getWorkoutDate());
        data.put("duration", request.getDuration().toString());
        data.put("types", String.join(", ", request.getTypes()));

        fcmService.sendPushNotificationToTokens(fcmTokens, title, body, data);
        log.info("Workout notification sent to {} members in room: {}", fcmTokens.size(), roomId);
    }

    /**
     * 채팅 메시지 알림 전송
     */
    @Transactional
    public void notifyChat(Member sender, Long roomId, NotifyChatRequest request) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND));

        // 운동방의 다른 멤버들에게 알림 전송 (본인 제외)
        List<FcmToken> fcmTokens = fcmTokenRepository.findActiveTokensByRoomIdExcludingMember(
                roomId, sender.getId()
        );

        if (fcmTokens.isEmpty()) {
            log.info("No active FCM tokens found for room: {}", roomId);
            return;
        }

        // 알림 제목과 내용 생성
        String title = workoutRoom.getName();
        String body = String.format("%s: %s", sender.getNickname(), truncateMessage(request.getMessage()));

        // 추가 데이터
        Map<String, String> data = new HashMap<>();
        data.put("type", "chat");
        data.put("roomId", roomId.toString());
        data.put("senderNickname", sender.getNickname());
        data.put("message", request.getMessage());

        fcmService.sendPushNotificationToTokens(fcmTokens, title, body, data);
        log.info("Chat notification sent to {} members in room: {}", fcmTokens.size(), roomId);
    }

    /**
     * 메시지 길이 제한 (너무 긴 메시지는 생략)
     */
    private String truncateMessage(String message) {
        int maxLength = 50;
        if (message.length() > maxLength) {
            return message.substring(0, maxLength) + "...";
        }
        return message;
    }
}