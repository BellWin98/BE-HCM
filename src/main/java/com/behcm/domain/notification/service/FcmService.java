package com.behcm.domain.notification.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.notification.entity.FcmToken;
import com.behcm.domain.notification.repository.FcmTokenRepository;
import com.google.firebase.messaging.*;
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
    public void sendGroupNotification(Long senderId, List<String> tokens, String title, String body, String path) {
        log.debug("알림 - title: {}, body: {}", title, body);
        for (String token : tokens) {
            try {
                Message message = Message.builder()
                        .setToken(token)
/*                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())*/
                        .putData("title", title)
                        .putData("body", body)
                        .putData("senderId", String.valueOf(senderId))
                        .putData("path", path != null ? path : "/")
                        .setAndroidConfig(AndroidConfig.builder()
                                .setTtl(0)
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .build())
                        .build();
                FirebaseMessaging.getInstance().send(message);
                log.debug("알림 발송 성공 (token: {})", token);
            } catch (Exception e) {
                log.error("FCM 발송 실패 (token: {}): {}", token, e.getMessage());
            }
        }
    }
}