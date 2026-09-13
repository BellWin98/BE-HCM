package com.behcm.domain.notification.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.notification.entity.FcmToken;
import com.behcm.domain.notification.repository.FcmTokenRepository;
import com.behcm.global.logging.LogMask;
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
    public void sendGroupNotification(Long senderId, List<String> tokens, String title, String body, String tag, String path) {
        for (String token : tokens) {
            try {
                Message message = Message.builder()
                        .setToken(token)
                        .putData("title", title)
                        .putData("body", body)
                        .putData("senderId", String.valueOf(senderId))
                        .putData("tag", tag)
                        .setAndroidConfig(AndroidConfig.builder()
                                .setTtl(0)
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .build())
                        .setWebpushConfig(WebpushConfig.builder()
                                .putHeader("Urgency", "high") // 백그라운드에서 깨우기 위함
                                .build())
                        .build();
                FirebaseMessaging.getInstance().send(message);
                log.debug("FCM sent (tag={}, token={})", tag, mask(token));
            } catch (FirebaseMessagingException e) {
                handleSendFailure(token, tag, e);
            } catch (Exception e) {
                // FirebaseApp 미초기화(IllegalStateException) 등 — 설정 문제라 개발자가 봐야 한다.
                log.error("FCM send failed unexpectedly (tag={}, token={})", tag, mask(token), e);
            }
        }
    }

    /**
     * 실패 사유별로 레벨을 나눈다. 전부 ERROR 로 찍으면 앱을 지운 사용자가 늘 때마다 알림이 울린다.
     * <ul>
     *   <li>UNREGISTERED / INVALID_ARGUMENT — 토큰이 죽었다(앱 삭제, 토큰 갱신). 정상적인 노후화이므로 WARN 이고,
     *       같은 토큰으로 계속 실패하지 않도록 지운다.</li>
     *   <li>UNAVAILABLE / INTERNAL / QUOTA_EXCEEDED — FCM 쪽 일시 장애. WARN, 토큰은 유지.</li>
     *   <li>SENDER_ID_MISMATCH / THIRD_PARTY_AUTH_ERROR — 서비스 계정·프로젝트 설정 오류. ERROR.</li>
     * </ul>
     */
    private void handleSendFailure(String token, String tag, FirebaseMessagingException e) {
        MessagingErrorCode code = e.getMessagingErrorCode();
        if (code == null) {
            log.error("FCM send failed without a messaging error code (tag={}, token={})", tag, mask(token), e);
            return;
        }
        switch (code) {
            case UNREGISTERED, INVALID_ARGUMENT -> {
                log.warn("FCM token is stale, removing it (code={}, tag={}, token={})", code, tag, mask(token));
                fcmTokenRepository.deleteByToken(token);
            }
            case UNAVAILABLE, INTERNAL, QUOTA_EXCEEDED ->
                    log.warn("FCM temporarily failed (code={}, tag={}, token={}): {}", code, tag, mask(token), e.getMessage());
            default ->
                    log.error("FCM rejected the request due to configuration (code={}, tag={}, token={}): {}",
                            code, tag, mask(token), e.getMessage());
        }
    }

    /** 토큰은 자격증명에 준한다. 같은 토큰인지 구분할 수 있을 만큼만 남긴다. */
    private static String mask(String token) {
        return LogMask.secret(token);
    }
}