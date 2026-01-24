package com.behcm.domain.notification.service;

import com.behcm.domain.notification.entity.FcmToken;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    /**
     * 단일 토큰으로 푸시 알림 전송
     */
    public void sendPushNotification(String token, String title, String body, Map<String, String> data) {
        try {
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putAllData(data != null ? data : Map.of())
                    .setWebpushConfig(WebpushConfig.builder()
                            .setNotification(WebpushNotification.builder()
                                    .setTitle(title)
                                    .setBody(body)
                                    .setIcon("/icon-192x192.png")
                                    .build())
                            .build())
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Successfully sent message: {}", response);
        } catch (FirebaseMessagingException e) {
            log.error("Failed to send FCM message to token: {}, error: {}", token, e.getMessage());
        }
    }

    /**
     * 여러 토큰으로 푸시 알림 전송 (배치)
     */
    public void sendPushNotificationToMultiple(List<String> tokens, String title, String body, Map<String, String> data) {
        if (tokens == null || tokens.isEmpty()) {
            log.warn("No tokens provided for push notification");
            return;
        }

        // FCM은 한 번에 최대 500개의 토큰만 지원
        int batchSize = 500;
        for (int i = 0; i < tokens.size(); i += batchSize) {
            List<String> batchTokens = tokens.subList(i, Math.min(i + batchSize, tokens.size()));
            sendBatch(batchTokens, title, body, data);
        }
    }

    /**
     * FcmToken 엔티티 리스트로 푸시 알림 전송
     */
    public void sendPushNotificationToTokens(List<FcmToken> fcmTokens, String title, String body, Map<String, String> data) {
        if (fcmTokens == null || fcmTokens.isEmpty()) {
            log.warn("No FCM tokens provided for push notification");
            return;
        }

        List<String> tokens = fcmTokens.stream()
                .map(FcmToken::getToken)
                .toList();

        sendPushNotificationToMultiple(tokens, title, body, data);
    }

    /**
     * 배치 전송 처리
     */
    private void sendBatch(List<String> tokens, String title, String body, Map<String, String> data) {
        try {
            MulticastMessage message = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putAllData(data != null ? data : Map.of())
                    .setWebpushConfig(WebpushConfig.builder()
                            .setNotification(WebpushNotification.builder()
                                    .setTitle(title)
                                    .setBody(body)
                                    .setIcon("/icon-192x192.png")
                                    .build())
                            .build())
                    .build();

            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            log.info("Successfully sent {} messages, {} failures",
                    response.getSuccessCount(), response.getFailureCount());

            // 실패한 토큰 로깅
            if (response.getFailureCount() > 0) {
                List<SendResponse> responses = response.getResponses();
                List<String> failedTokens = new ArrayList<>();

                for (int i = 0; i < responses.size(); i++) {
                    if (!responses.get(i).isSuccessful()) {
                        failedTokens.add(tokens.get(i));
                        log.error("Failed to send to token {}: {}",
                                tokens.get(i),
                                responses.get(i).getException().getMessage());
                    }
                }
            }
        } catch (FirebaseMessagingException e) {
            log.error("Failed to send batch FCM messages: {}", e.getMessage());
        }
    }
}