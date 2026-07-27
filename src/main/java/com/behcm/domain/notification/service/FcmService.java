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

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    /** sendEachForMulticast 1회 호출당 최대 토큰 수 (FCM 제한) */
    private static final int MULTICAST_LIMIT = 500;
    /** 알림 클릭 시 이동할 기본 경로 (path 미지정 시) */
    private static final String DEFAULT_PATH = "/dashboard";

    private final FcmTokenRepository fcmTokenRepository;
    private final FcmTokenCleanupService fcmTokenCleanupService;

    @Transactional
    public void saveFcmToken(Member member, String token) {
        // 동일 토큰이 다른 회원에게 매핑돼 있으면(기기 재로그인/재설치 등) 유니크 제약 충돌을 막기 위해 먼저 정리한다.
        fcmTokenRepository.findByToken(token)
                .filter(existing -> !existing.getMember().getId().equals(member.getId()))
                .ifPresent(existing -> {
                    fcmTokenRepository.delete(existing);
                    fcmTokenRepository.flush();
                });

        fcmTokenRepository.findByMember(member)
                .ifPresentOrElse(t -> t.updateToken(token),
                        () -> fcmTokenRepository.save(new FcmToken(member, token)));
    }

    /**
     * 로그아웃 등에서 해당 회원 소유의 토큰만 제거한다(타 회원 토큰은 건드리지 않음).
     */
    @Transactional
    public void deleteFcmToken(Member member, String token) {
        fcmTokenRepository.findByToken(token)
                .filter(t -> t.getMember().getId().equals(member.getId()))
                .ifPresent(fcmTokenRepository::delete);
    }

    @Async("fcmExecutor")
    public void sendGroupNotification(Long senderId, List<String> tokens, String title, String body, String tag, String path) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        String url = (path == null || path.isBlank()) ? DEFAULT_PATH : path;

        List<String> staleTokens = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i += MULTICAST_LIMIT) {
            List<String> chunk = tokens.subList(i, Math.min(i + MULTICAST_LIMIT, tokens.size()));
            staleTokens.addAll(sendChunk(senderId, chunk, title, body, tag, url));
        }

        if (!staleTokens.isEmpty()) {
            fcmTokenCleanupService.deleteStaleTokens(staleTokens);
            log.info("만료된 FCM 토큰 {}건 정리 완료", staleTokens.size());
        }
    }

    /**
     * 토큰 청크 하나를 멀티캐스트로 발송하고, 만료된 토큰 목록을 반환한다.
     * <p>
     * firebase-admin 9.10.0(2026-07)에서 {@code token/tokens}(→ {@code addAllTokens})가
     * {@code fid/fids} 방식으로 전환되며 deprecated 되었으나, (1) 등록 토큰은 아직 정상 동작하고
     * 제거 기한도 없으며, (2) FID 기반 타깃 전송은 문서화되지 않았고 특히 웹 푸시(JS SDK
     * getToken + VAPID) 예제가 없어 지금 이전하면 전송이 깨질 위험이 크다. 따라서 Firebase가
     * FID 웹 전송을 문서화할 때까지 토큰 경로를 유지하고 경고만 억제한다.
     */
    @SuppressWarnings("deprecation")
    private List<String> sendChunk(Long senderId, List<String> tokens, String title, String body, String tag, String url) {
        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .putData("title", title)
                .putData("body", body)
                .putData("senderId", String.valueOf(senderId))
                .putData("tag", tag)
                .putData("url", url)
                .setAndroidConfig(AndroidConfig.builder()
                        .setTtl(0)
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .build())
                .setWebpushConfig(WebpushConfig.builder()
                        .putHeader("Urgency", "high") // 백그라운드에서 깨우기 위함
                        .build())
                .build();

        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            log.debug("FCM 배치 발송 - 성공 {}건, 실패 {}건", response.getSuccessCount(), response.getFailureCount());
            return collectStaleTokens(tokens, response);
        } catch (FirebaseMessagingException e) {
            log.error("FCM 배치 발송 실패 (count: {}): {}", tokens.size(), e.getMessage());
            return List.of();
        }
    }

    /**
     * 배치 응답에서 더 이상 유효하지 않은(등록 해제된) 토큰만 추려낸다.
     * <p>
     * {@code UNREGISTERED}는 앱 삭제/토큰 폐기로 확정적으로 무효인 토큰이므로 삭제 대상이다.
     * {@code INVALID_ARGUMENT}는 잘못된 요청(payload) 때문일 수도 있어 일괄 삭제 시 유효 토큰까지
     * 지울 위험이 있으므로 삭제하지 않고 로그만 남긴다.
     */
    private List<String> collectStaleTokens(List<String> tokens, BatchResponse response) {
        List<String> stale = new ArrayList<>();
        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse r = responses.get(i);
            if (r.isSuccessful()) {
                continue;
            }
            FirebaseMessagingException ex = r.getException();
            MessagingErrorCode code = ex != null ? ex.getMessagingErrorCode() : null;
            if (code == MessagingErrorCode.UNREGISTERED) {
                stale.add(tokens.get(i));
            } else {
                log.error("FCM 발송 실패 (token: {}, code: {})", tokens.get(i), code);
            }
        }
        return stale;
    }
}
