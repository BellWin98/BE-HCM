package com.behcm.domain.rest.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.notification.service.NotificationFacade;
import com.behcm.domain.rest.dto.RestRequest;
import com.behcm.domain.rest.entity.Rest;
import com.behcm.domain.rest.repository.RestRepository;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RestService {

    private static final String REST_DAY_REGISTERED_TYPE = "REST";

    private final RestRepository restRepository;
    private final WorkoutRoomMemberRepository workoutRoomMemberRepository;
    private final NotificationFacade notificationFacade;

    public void registerRestDay(Member member, RestRequest request) {
        List<WorkoutRoomMember> wrms = workoutRoomMemberRepository.findByMember(member);
        if (wrms.isEmpty()) {
            throw new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND);
        }
        LocalDate startDate = LocalDate.parse(request.getStartDate());
        LocalDate endDate = LocalDate.parse(request.getEndDate());

        List<Rest> existingRests = restRepository.findAllByWorkoutRoomMemberIn(wrms);
        boolean hasOverlap = existingRests.stream()
                .anyMatch(rest -> isOverlapping(rest.getStartDate(), rest.getEndDate(), startDate, endDate));

        if (hasOverlap) {
            throw new CustomException(ErrorCode.REST_PERIOD_OVERLAP);
        }

        for (WorkoutRoomMember wrm : wrms) {
            Rest rest = Rest.builder()
                    .workoutRoomMember(wrm)
                    .reason(request.getReason())
                    .startDate(startDate)
                    .endDate(endDate)
                    .build();
            restRepository.save(rest);
            notifyRestDayRegistered(member, wrm.getWorkoutRoom(), startDate, endDate);
        }
    }

    private void notifyRestDayRegistered(Member member, WorkoutRoom workoutRoom, LocalDate startDate, LocalDate endDate) {
        String title = String.format("%s님이 휴식일을 등록했어요!", member.getNickname());
        String body = String.format("%s ~ %s",
                startDate.format(DateTimeFormatter.ISO_LOCAL_DATE), endDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
        notificationFacade.notifyRoomMembers(workoutRoom.getId(), member, title, body, REST_DAY_REGISTERED_TYPE, "");
    }

    private boolean isOverlapping(LocalDate existingStart, LocalDate existingEnd,
                                  LocalDate newStart, LocalDate newEnd) {
        return !(existingEnd.isBefore(newStart) || existingStart.isAfter(newEnd));
    }
}
