package com.behcm.domain.notification.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.notification.repository.FcmTokenRepository;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
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
@Transactional(readOnly = true)
public class NotificationFacade {

    private final WorkoutRoomRepository workoutRoomRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final FcmService fcmService;

    public void registerFcmToken(Member member, String token) {
        fcmService.saveFcmToken(member, token);
        log.debug("토큰 등록 완료 - member: {}, token: {}", member.getEmail(), token);
    }

    public void notifyAllRoomMembers(Member member, String title, String body, String type, String path) {
        List<String> fcmTokens = fcmTokenRepository.findFcmTokensByMember(member);
        fcmService.sendGroupNotification(member.getId(), fcmTokens, title, body, type + "-" + member.getId(), path);
    }

    public void notifyRoomMembers(Long roomId, Member member, String title, String body, String type, String path) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findByIdAndIsActiveTrue(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND));

        List<Member> targetMembers = workoutRoom.getWorkoutRoomMembers().stream()
                .map(WorkoutRoomMember::getMember)
                .filter(m -> !m.getId().equals(member.getId()))
                .toList();

        List<String> fcmTokens = fcmTokenRepository.findFcmTokensByMembers(targetMembers);

        fcmService.sendGroupNotification(member.getId(), fcmTokens, title, body, type + "-" + roomId, path);
    }
}