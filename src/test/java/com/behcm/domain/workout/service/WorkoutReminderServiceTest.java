package com.behcm.domain.workout.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.notification.service.NotificationFacade;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkoutReminderServiceTest {

    @Mock
    private WorkoutRoomMemberRepository workoutRoomMemberRepository;

    @Mock
    private NotificationFacade notificationFacade;

    @InjectMocks
    private WorkoutReminderService workoutReminderService;

    private Member member(String nickname) {
        return Member.builder()
                .email(nickname + "@test.com")
                .nickname(nickname)
                .role(MemberRole.USER)
                .build();
    }

    private WorkoutRoom room(String name) {
        return WorkoutRoom.builder()
                .name(name)
                .minWeeklyWorkouts(3)
                .penaltyEnabled(false)
                .maxMembers(10)
                .entryCode("ENTRY01")
                .owner(member("owner"))
                .build();
    }

    @Test
    @DisplayName("오늘 인증하지 않은 멤버가 없으면 알림을 보내지 않는다")
    void remindUnauthenticatedMembers_noPendingMembers_sendsNothing() {
        given(workoutRoomMemberRepository.findMembersWithoutWorkoutRecordOn(any(LocalDate.class)))
                .willReturn(List.of());

        workoutReminderService.remindUnauthenticatedMembers();

        verify(notificationFacade, never()).notifyMember(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("오늘 인증하지 않은 멤버에게 방의 인증 현황을 포함한 개인 알림을 보낸다")
    void remindUnauthenticatedMembers_notifiesPendingMembersWithRoomProgress() {
        WorkoutRoom workoutRoom = room("아침 러닝방");
        Member authenticatedMember = member("done");
        Member pendingMember = member("pending");
        workoutRoom.getWorkoutRoomMembers().add(WorkoutRoomMember.builder().member(authenticatedMember).workoutRoom(workoutRoom).build());
        WorkoutRoomMember pendingWrm = WorkoutRoomMember.builder().member(pendingMember).workoutRoom(workoutRoom).build();
        workoutRoom.getWorkoutRoomMembers().add(pendingWrm);

        given(workoutRoomMemberRepository.findMembersWithoutWorkoutRecordOn(any(LocalDate.class)))
                .willReturn(List.of(pendingWrm));

        workoutReminderService.remindUnauthenticatedMembers();

        verify(notificationFacade).notifyMember(
                eq(pendingMember),
                anyString(),
                eq("아침 러닝방에서 오늘 1명이 인증했어요!"),
                eq("UNAUTHENTICATED_REMINDER"),
                eq("")
        );
    }

    @Test
    @DisplayName("오늘 아무도 인증하지 않았으면 '0명 인증' 대신 첫 인증 독려 문구를 보낸다")
    void remindUnauthenticatedMembers_noOneAuthenticated_sendsFirstAuthenticationEncouragement() {
        WorkoutRoom workoutRoom = room("아침 러닝방");
        Member pendingMember1 = member("pending1");
        Member pendingMember2 = member("pending2");
        WorkoutRoomMember pendingWrm1 = WorkoutRoomMember.builder().member(pendingMember1).workoutRoom(workoutRoom).build();
        WorkoutRoomMember pendingWrm2 = WorkoutRoomMember.builder().member(pendingMember2).workoutRoom(workoutRoom).build();
        workoutRoom.getWorkoutRoomMembers().add(pendingWrm1);
        workoutRoom.getWorkoutRoomMembers().add(pendingWrm2);

        given(workoutRoomMemberRepository.findMembersWithoutWorkoutRecordOn(any(LocalDate.class)))
                .willReturn(List.of(pendingWrm1, pendingWrm2));

        workoutReminderService.remindUnauthenticatedMembers();

        verify(notificationFacade).notifyMember(
                eq(pendingMember1),
                anyString(),
                eq("아침 러닝방에서 오늘 첫 운동 인증의 주인공이 되어보세요!"),
                eq("UNAUTHENTICATED_REMINDER"),
                eq("")
        );
    }
}
