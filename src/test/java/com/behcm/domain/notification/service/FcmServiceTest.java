package com.behcm.domain.notification.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.notification.entity.FcmToken;
import com.behcm.domain.notification.repository.FcmTokenRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FcmServiceTest {

    @Mock
    private FcmTokenRepository fcmTokenRepository;

    @InjectMocks
    private FcmService fcmService;

    private Member member() {
        return Member.builder()
                .email("user@test.com")
                .nickname("user")
                .role(MemberRole.USER)
                .build();
    }

    @Test
    @DisplayName("saveFcmToken은 기존 토큰이 있으면 갱신하고 새로 저장하지 않는다")
    void saveFcmToken_existingToken_updatesInPlace() {
        Member member = member();
        FcmToken existing = FcmToken.builder().member(member).token("old-token").build();
        given(fcmTokenRepository.findByMember(member)).willReturn(Optional.of(existing));

        fcmService.saveFcmToken(member, "new-token");

        assertThat(existing.getToken()).isEqualTo("new-token");
        verify(fcmTokenRepository, never()).save(any(FcmToken.class));
    }

    @Test
    @DisplayName("saveFcmToken은 기존 토큰이 없으면 새로 저장한다")
    void saveFcmToken_noExistingToken_savesNew() {
        Member member = member();
        given(fcmTokenRepository.findByMember(member)).willReturn(Optional.empty());

        fcmService.saveFcmToken(member, "new-token");

        verify(fcmTokenRepository).save(any(FcmToken.class));
    }

    @Test
    @DisplayName("sendGroupNotification은 전달받은 모든 토큰 각각에 대해 FCM 메시지를 발송한다")
    void sendGroupNotification_sendsToEveryToken() throws FirebaseMessagingException {
        try (MockedStatic<FirebaseMessaging> mockedStatic = Mockito.mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging messagingMock = Mockito.mock(FirebaseMessaging.class);
            mockedStatic.when(FirebaseMessaging::getInstance).thenReturn(messagingMock);
            given(messagingMock.send(any(Message.class))).willReturn("message-id");

            fcmService.sendGroupNotification(1L, List.of("token-a", "token-b"), "title", "body", "tag", "/path");

            verify(messagingMock, times(2)).send(any(Message.class));
        }
    }

    @Test
    @DisplayName("sendGroupNotification은 특정 토큰 발송이 실패해도 나머지 토큰 발송을 계속 진행한다")
    void sendGroupNotification_oneTokenFails_continuesWithRemainingTokens() throws FirebaseMessagingException {
        try (MockedStatic<FirebaseMessaging> mockedStatic = Mockito.mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging messagingMock = Mockito.mock(FirebaseMessaging.class);
            mockedStatic.when(FirebaseMessaging::getInstance).thenReturn(messagingMock);
            given(messagingMock.send(any(Message.class)))
                    .willThrow(new RuntimeException("fcm down"))
                    .willReturn("message-id");

            fcmService.sendGroupNotification(1L, List.of("bad-token", "good-token"), "title", "body", "tag", "/path");

            verify(messagingMock, times(2)).send(any(Message.class));
        }
    }
}
