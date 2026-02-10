package com.behcm.domain.notification.service;

import com.behcm.domain.member.entity.Member;
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

import java.util.List;
import java.util.Objects;

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

    public void notifyRoomMembers(Long roomId, Member member, String title, String body, String path, String type) {
        WorkoutRoom workoutRoom = workoutRoomRepository.findByIdAndIsActiveTrue(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND));

        log.debug("member: {}", member.getNickname());

        List<String> tokens = workoutRoom.getWorkoutRoomMembers().stream()
                .filter(workoutRoomMember -> !workoutRoomMember.getMember().equals(member))
                .map(workoutRoomMember -> fcmTokenRepository.findByMember(workoutRoomMember.getMember())
                        .map(FcmToken::getToken).orElse(null))
                .filter(Objects::nonNull)
                .toList();

        log.debug("roomId: {}, token count: {}", roomId, tokens.size());

        fcmService.sendGroupNotification(tokens, title, body, path, type);
    }
}