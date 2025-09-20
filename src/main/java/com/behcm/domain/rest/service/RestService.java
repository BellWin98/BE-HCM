package com.behcm.domain.rest.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.rest.dto.RestRequest;
import com.behcm.domain.rest.entity.Rest;
import com.behcm.domain.rest.repository.RestRepository;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RestService {
    private final RestRepository restRepository;
    private final WorkoutRoomMemberRepository workoutRoomMemberRepository;

    public void registerRestDay(Member member, RestRequest request) {
        List<WorkoutRoomMember> wrms = workoutRoomMemberRepository.findWorkoutRoomMembersByMember(member);
        if (wrms.isEmpty()) {
            throw new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND);
        }
        LocalDate startDate = LocalDate.parse(request.getStartDate());
        LocalDate endDate = LocalDate.parse(request.getEndDate());

        for (WorkoutRoomMember wrm : wrms) {
            Rest rest = Rest.builder()
                    .workoutRoomMember(wrm)
                    .reason(request.getReason())
                    .startDate(startDate)
                    .endDate(endDate)
                    .build();
            restRepository.save(rest);
        }
    }
}
