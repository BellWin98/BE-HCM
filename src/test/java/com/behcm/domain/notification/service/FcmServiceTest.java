package com.behcm.domain.notification.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.notification.entity.FcmToken;
import com.behcm.domain.notification.repository.FcmTokenRepository;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
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

    @Mock
    private FcmTokenCleanupService fcmTokenCleanupService;

    @InjectMocks
    private FcmService fcmService;

    private void setId(Object entity, long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Member member(long id) {
        Member m = Member.builder()
                .email("user" + id + "@test.com")
                .nickname("user" + id)
                .role(MemberRole.USER)
                .build();
        setId(m, id);
        return m;
    }

    @Test
    @DisplayName("saveFcmToken은 기존 토큰이 있으면 갱신하고 새로 저장하지 않는다")
    void saveFcmToken_existingToken_updatesInPlace() {
        Member member = member(1L);
        FcmToken existing = FcmToken.builder().member(member).token("old-token").build();
        given(fcmTokenRepository.findByMember(member)).willReturn(Optional.of(existing));

        fcmService.saveFcmToken(member, "new-token");

        assertThat(existing.getToken()).isEqualTo("new-token");
        verify(fcmTokenRepository, never()).save(any(FcmToken.class));
    }

    @Test
    @DisplayName("saveFcmToken은 기존 토큰이 없으면 새로 저장한다")
    void saveFcmToken_noExistingToken_savesNew() {
        Member member = member(1L);
        given(fcmTokenRepository.findByMember(member)).willReturn(Optional.empty());

        fcmService.saveFcmToken(member, "new-token");

        verify(fcmTokenRepository).save(any(FcmToken.class));
    }

    @Test
    @DisplayName("saveFcmToken은 동일 토큰이 다른 회원 소유면 기존 매핑을 제거한 뒤 저장한다")
    void saveFcmToken_tokenOwnedByAnotherMember_reassigns() {
        Member owner = member(1L);
        Member newOwner = member(2L);
        FcmToken conflicting = FcmToken.builder().member(owner).token("shared-token").build();
        given(fcmTokenRepository.findByToken("shared-token")).willReturn(Optional.of(conflicting));
        given(fcmTokenRepository.findByMember(newOwner)).willReturn(Optional.empty());

        fcmService.saveFcmToken(newOwner, "shared-token");

        verify(fcmTokenRepository).delete(conflicting);
        verify(fcmTokenRepository).flush();
        verify(fcmTokenRepository).save(any(FcmToken.class));
    }

    @Test
    @DisplayName("deleteFcmToken은 본인 소유 토큰만 삭제한다")
    void deleteFcmToken_ownToken_deletes() {
        Member member = member(1L);
        FcmToken own = FcmToken.builder().member(member).token("my-token").build();
        given(fcmTokenRepository.findByToken("my-token")).willReturn(Optional.of(own));

        fcmService.deleteFcmToken(member, "my-token");

        verify(fcmTokenRepository).delete(own);
    }

    @Test
    @DisplayName("deleteFcmToken은 타 회원 소유 토큰은 삭제하지 않는다")
    void deleteFcmToken_othersToken_doesNotDelete() {
        Member requester = member(1L);
        Member other = member(2L);
        FcmToken othersToken = FcmToken.builder().member(other).token("other-token").build();
        given(fcmTokenRepository.findByToken("other-token")).willReturn(Optional.of(othersToken));

        fcmService.deleteFcmToken(requester, "other-token");

        verify(fcmTokenRepository, never()).delete(any(FcmToken.class));
    }

    @Test
    @DisplayName("sendGroupNotification은 토큰들을 멀티캐스트로 한 번에 발송한다")
    void sendGroupNotification_sendsMulticast() throws FirebaseMessagingException {
        try (MockedStatic<FirebaseMessaging> mockedStatic = Mockito.mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging messagingMock = Mockito.mock(FirebaseMessaging.class);
            mockedStatic.when(FirebaseMessaging::getInstance).thenReturn(messagingMock);

            BatchResponse batchResponse = Mockito.mock(BatchResponse.class);
            SendResponse ok = Mockito.mock(SendResponse.class);
            given(ok.isSuccessful()).willReturn(true);
            given(batchResponse.getResponses()).willReturn(List.of(ok, ok));
            given(messagingMock.sendEachForMulticast(any(MulticastMessage.class))).willReturn(batchResponse);

            fcmService.sendGroupNotification(1L, List.of("token-a", "token-b"), "title", "body", "tag", "/path");

            verify(messagingMock, times(1)).sendEachForMulticast(any(MulticastMessage.class));
            verify(fcmTokenCleanupService, never()).deleteStaleTokens(any());
        }
    }

    @Test
    @DisplayName("sendGroupNotification은 UNREGISTERED 응답을 받은 토큰을 정리 대상으로 넘긴다")
    void sendGroupNotification_unregisteredToken_cleansUp() throws FirebaseMessagingException {
        try (MockedStatic<FirebaseMessaging> mockedStatic = Mockito.mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging messagingMock = Mockito.mock(FirebaseMessaging.class);
            mockedStatic.when(FirebaseMessaging::getInstance).thenReturn(messagingMock);

            SendResponse ok = Mockito.mock(SendResponse.class);
            given(ok.isSuccessful()).willReturn(true);

            FirebaseMessagingException unregisteredEx = Mockito.mock(FirebaseMessagingException.class);
            given(unregisteredEx.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNREGISTERED);
            SendResponse failed = Mockito.mock(SendResponse.class);
            given(failed.isSuccessful()).willReturn(false);
            given(failed.getException()).willReturn(unregisteredEx);

            BatchResponse batchResponse = Mockito.mock(BatchResponse.class);
            // 순서: bad-token(실패), good-token(성공)
            given(batchResponse.getResponses()).willReturn(List.of(failed, ok));
            given(messagingMock.sendEachForMulticast(any(MulticastMessage.class))).willReturn(batchResponse);

            fcmService.sendGroupNotification(1L, List.of("bad-token", "good-token"), "title", "body", "tag", "/path");

            verify(fcmTokenCleanupService).deleteStaleTokens(List.of("bad-token"));
        }
    }

    @Test
    @DisplayName("sendGroupNotification은 토큰이 비어있으면 아무것도 발송하지 않는다")
    void sendGroupNotification_emptyTokens_noOp() throws FirebaseMessagingException {
        try (MockedStatic<FirebaseMessaging> mockedStatic = Mockito.mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging messagingMock = Mockito.mock(FirebaseMessaging.class);

            fcmService.sendGroupNotification(1L, List.of(), "title", "body", "tag", "/path");

            mockedStatic.verifyNoInteractions();
            verify(messagingMock, never()).sendEachForMulticast(any(MulticastMessage.class));
        }
    }
}
