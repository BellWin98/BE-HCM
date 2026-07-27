package com.behcm.domain.notification.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.notification.entity.FcmToken;
import com.behcm.domain.notification.repository.FcmTokenRepository;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import com.behcm.domain.workout.repository.WorkoutRoomRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationFacadeTest {

    @Mock
    private WorkoutRoomRepository workoutRoomRepository;

    @Mock
    private WorkoutRoomMemberRepository workoutRoomMemberRepository;

    @Mock
    private FcmTokenRepository fcmTokenRepository;

    @Mock
    private FcmService fcmService;

    @InjectMocks
    private NotificationFacade notificationFacade;

    private void setId(Object entity, long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Member member(long id, String nickname) {
        Member m = Member.builder()
                .email(nickname + "@test.com")
                .nickname(nickname)
                .role(MemberRole.USER)
                .build();
        setId(m, id);
        return m;
    }

    private WorkoutRoom room(long id, Member owner) {
        WorkoutRoom room = WorkoutRoom.builder()
                .name("Test Room")
                .minWeeklyWorkouts(3)
                .penaltyEnabled(false)
                .maxMembers(10)
                .entryCode("ENTRY01")
                .owner(owner)
                .build();
        setId(room, id);
        return room;
    }

    @Test
    @DisplayName("registerFcmToken은 FcmService에 토큰 저장을 위임한다")
    void registerFcmToken_delegatesToFcmService() {
        Member member = member(1L, "user");

        notificationFacade.registerFcmToken(member, "fcm-token");

        verify(fcmService).saveFcmToken(member, "fcm-token");
    }

    @Test
    @DisplayName("notifyAllRoomMembers는 회원이 참여한 모든 방의 멤버 토큰으로 알림을 발송한다")
    void notifyAllRoomMembers_sendsToTokensAcrossAllRooms() {
        Member member = member(1L, "user");
        given(fcmTokenRepository.findFcmTokensByMember(member)).willReturn(List.of("token-a", "token-b"));

        notificationFacade.notifyAllRoomMembers(member, "title", "body", "WORKOUT", "/path");

        verify(fcmService).sendGroupNotification(1L, List.of("token-a", "token-b"), "title", "body", "WORKOUT-1", "/path");
    }

    @Test
    @DisplayName("notifyMember는 대상 회원의 토큰으로만 알림을 발송한다")
    void notifyMember_sendsToTargetMemberToken() {
        Member target = member(1L, "target");
        FcmToken fcmToken = new FcmToken(target, "target-token");
        given(fcmTokenRepository.findByMember(target)).willReturn(Optional.of(fcmToken));

        notificationFacade.notifyMember(target, "title", "body", "PENALTY_ASSIGNED", "/path");

        verify(fcmService).sendGroupNotification(1L, List.of("target-token"), "title", "body", "PENALTY_ASSIGNED-1", "/path");
    }

    @Test
    @DisplayName("notifyMember는 대상 회원의 토큰이 없으면 발송하지 않는다")
    void notifyMember_noToken_doesNotSend() {
        Member target = member(1L, "target");
        given(fcmTokenRepository.findByMember(target)).willReturn(Optional.empty());

        notificationFacade.notifyMember(target, "title", "body", "PENALTY_ASSIGNED", "/path");

        verify(fcmService, never()).sendGroupNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("notifyRoomMembers는 운동방이 없으면 WORKOUT_ROOM_NOT_FOUND 예외를 던진다")
    void notifyRoomMembers_roomNotFound_throwsWorkoutRoomNotFound() {
        Member member = member(1L, "user");
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationFacade.notifyRoomMembers(1L, member, "title", "body", "CHAT", "/path"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WORKOUT_ROOM_NOT_FOUND);

        verify(fcmService, never()).sendGroupNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("notifyRoomMembers는 발신자를 제외한 나머지 방 멤버들에게만 알림을 발송한다")
    void notifyRoomMembers_excludesSenderFromTargets() {
        Member sender = member(1L, "sender");
        Member other = member(2L, "other");
        WorkoutRoom room = room(1L, sender);
        WorkoutRoomMember senderWrm = WorkoutRoomMember.builder().member(sender).workoutRoom(room).build();
        WorkoutRoomMember otherWrm = WorkoutRoomMember.builder().member(other).workoutRoom(room).build();
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(room));
        given(workoutRoomMemberRepository.existsByMemberAndWorkoutRoom(sender, room)).willReturn(true);
        given(workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAtFetchMember(room))
                .willReturn(List.of(senderWrm, otherWrm));
        given(fcmTokenRepository.findFcmTokensByMembers(List.of(other))).willReturn(List.of("other-token"));

        notificationFacade.notifyRoomMembers(1L, sender, "title", "body", "CHAT", "/path");

        verify(fcmService).sendGroupNotification(eq(1L), eq(List.of("other-token")), eq("title"), eq("body"), eq("CHAT-1"), eq("/path"));
        verify(fcmTokenRepository).findFcmTokensByMembers(List.of(other));
        verify(workoutRoomMemberRepository).findByWorkoutRoomOrderByJoinedAtFetchMember(room);
    }

    @Test
    @DisplayName("notifyRoomMembers는 발신자가 방 멤버가 아니면 NOT_WORKOUT_ROOM_MEMBER 예외를 던진다")
    void notifyRoomMembers_notMember_throwsNotWorkoutRoomMember() {
        Member outsider = member(9L, "outsider");
        Member owner = member(1L, "owner");
        WorkoutRoom room = room(1L, owner);
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(room));
        given(workoutRoomMemberRepository.existsByMemberAndWorkoutRoom(outsider, room)).willReturn(false);

        assertThatThrownBy(() -> notificationFacade.notifyRoomMembers(1L, outsider, "title", "body", "CHAT", "/path"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_WORKOUT_ROOM_MEMBER);

        verify(fcmService, never()).sendGroupNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("unregisterFcmToken은 FcmService에 토큰 삭제를 위임한다")
    void unregisterFcmToken_delegatesToFcmService() {
        Member member = member(1L, "user");

        notificationFacade.unregisterFcmToken(member, "fcm-token");

        verify(fcmService).deleteFcmToken(member, "fcm-token");
    }
}
