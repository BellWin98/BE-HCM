package com.behcm.domain.notification.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.notification.entity.FcmToken;
import com.behcm.domain.notification.repository.FcmTokenRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    private final FcmTokenRepository fcmTokenRepository;

    @Transactional
    public void saveFcmToken(Member member, String token) {
        fcmTokenRepository.findByMember(member)
                .ifPresentOrElse(t -> t.updateToken(token),
                        () -> fcmTokenRepository.save(new FcmToken(member, token)));
    }

    @Async
    public void sendGroupNotification(List<String> tokens, String title, String body, String path, String type) {
        for (String token : tokens) {
            try {
                Message message = Message.builder()
                        .setToken(token)
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .build();
                FirebaseMessaging.getInstance().send(message);
                log.info("알림 발송 성공 (token: {}, title: {}, body: {}, type: {}, path: {})", token, title, body, type, path);
            } catch (Exception e) {
                log.error("FCM 발송 실패 (token: {}): {}", token, e.getMessage());
            }
        }
    }
}