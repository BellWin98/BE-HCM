package com.behcm.domain.notification.service;

import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class NotificationService {

    @Async("threadPoolTaskExecutor")
    public void sendWorkoutNotification(List<String> tokens, String nickname) {
        if (tokens.isEmpty()) {
            return;
        }

        String title = "새로운 운동 인증!";
        String body = nickname + "님이 방금 운동을 인증했어요. 확인해보세요! 💪";

        MulticastMessage message = MulticastMessage.builder()
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putData("type", "WORKOUT")
                .addAllTokens(tokens)
                .build();


        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            if (response.getFailureCount() > 0) {
                List<SendResponse> responses = response.getResponses();
                for (int i = 0; i < responses.size(); i++) {
                    if (!responses.get(i).isSuccessful()) {
                        log.error("Failed to send notification to token: {}, error: {}",
                                tokens.get(i), responses.get(i).getException().getMessage());
                    }
                }
            }
            log.info("Successfully sent {} notifications.", response.getSuccessCount());
        } catch (FirebaseMessagingException e) {
            log.error("Error sending FCM message: {}", e.getMessage());
        }
    }
}
