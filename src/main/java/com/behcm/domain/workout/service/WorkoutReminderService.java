package com.behcm.domain.workout.service;

import com.behcm.domain.notification.service.NotificationFacade;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkoutReminderService {

    private static final String UNAUTHENTICATED_REMINDER_TYPE = "UNAUTHENTICATED_REMINDER";

    private final WorkoutRoomMemberRepository workoutRoomMemberRepository;
    private final NotificationFacade notificationFacade;

    @Scheduled(cron = "0 0 21 * * *")
    @Transactional(readOnly = true)
    public void remindUnauthenticatedMembers() {
        log.info("Starting unauthenticated workout reminder");

        List<WorkoutRoomMember> unauthenticatedMembers =
                workoutRoomMemberRepository.findMembersWithoutWorkoutRecordOn(LocalDate.now());

        Map<WorkoutRoom, List<WorkoutRoomMember>> unauthenticatedByRoom = unauthenticatedMembers.stream()
                .collect(Collectors.groupingBy(WorkoutRoomMember::getWorkoutRoom));

        unauthenticatedByRoom.forEach(this::remindRoomMembers);

        log.info("Unauthenticated workout reminder completed - {} members notified", unauthenticatedMembers.size());
    }

    private void remindRoomMembers(WorkoutRoom workoutRoom, List<WorkoutRoomMember> pendingMembers) {
        int totalMembers = workoutRoom.getCurrentMemberCount();
        int authenticatedCount = totalMembers - pendingMembers.size();

        for (WorkoutRoomMember pendingMember : pendingMembers) {
            sendReminder(pendingMember, workoutRoom, authenticatedCount, totalMembers);
        }
    }

    private void sendReminder(WorkoutRoomMember pendingMember, WorkoutRoom workoutRoom,
                               int authenticatedCount, int totalMembers) {
        String title = "⏰ 오늘 운동 인증 잊지 마세요";
        String body = String.format("%s에서 %s님 아직 인증 전이에요. 벌써 %d/%d명이 인증했어요!",
                workoutRoom.getName(), pendingMember.getNickname(), authenticatedCount, totalMembers);

        notificationFacade.notifyMember(pendingMember.getMember(), title, body, UNAUTHENTICATED_REMINDER_TYPE, "");
    }
}
