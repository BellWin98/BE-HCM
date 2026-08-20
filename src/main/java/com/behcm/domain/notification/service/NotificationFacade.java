package com.behcm.domain.notification.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.notification.repository.FcmTokenRepository;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import com.behcm.domain.workout.repository.WorkoutRoomRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationFacade {

    private final WorkoutRoomRepository workoutRoomRepository;
    private final WorkoutRoomMemberRepository workoutRoomMemberRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final FcmService fcmService;

    public void registerFcmToken(Member member, String token) {
        fcmService.saveFcmToken(member, token);
        log.debug("토큰 등록 완료 - member: {}, token: {}", member.getEmail(), token);
    }

    public void notifyMember(Member targetMember, String title, String body, String type, String path) {
        fcmTokenRepository.findByMember(targetMember)
                .ifPresent(fcmToken -> fcmService.sendGroupNotification(
                        targetMember.getId(), List.of(fcmToken.getToken()), title, truncateMessage(body),
                        type + "-" + targetMember.getId(), path));
    }

    public void notifyRoomMembers(Long roomId, Member member, String title, String body, String type, String path) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findByIdAndIsActiveTrue(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND));

        List<Member> targetMembers = workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAtFetchMember(workoutRoom).stream()
                .map(WorkoutRoomMember::getMember)
                .filter(m -> !m.getId().equals(member.getId()))
                .toList();

        List<String> fcmTokens = fcmTokenRepository.findFcmTokensByMembers(targetMembers);

        fcmService.sendGroupNotification(member.getId(), fcmTokens, title, truncateMessage(body), type + "-" + roomId, path);
    }

    /**
     * 메시지 길이 제한 (너무 긴 메시지는 생략)
     */
    private String truncateMessage(String message) {
        int maxLength = 50;
        if (message != null && message.length() > maxLength) {
            return message.substring(0, maxLength) + "...";
        }
        return message;
    }
}