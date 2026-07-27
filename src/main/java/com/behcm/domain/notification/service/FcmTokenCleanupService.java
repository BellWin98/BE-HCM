package com.behcm.domain.notification.service;

import com.behcm.domain.notification.repository.FcmTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 만료/무효 FCM 토큰을 DB에서 정리한다.
 * <p>
 * {@link FcmService#sendGroupNotification}이 {@code @Async}로 동작하므로, 같은 빈 내부에서
 * {@code @Transactional} 메서드를 self-invocation 하면 프록시가 적용되지 않는다. 이를 피하기 위해
 * 삭제 로직을 별도 빈으로 분리해 트랜잭션이 정상 적용되도록 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FcmTokenCleanupService {

    private final FcmTokenRepository fcmTokenRepository;

    @Transactional
    public void deleteStaleTokens(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        fcmTokenRepository.deleteByTokenIn(tokens);
    }
}
