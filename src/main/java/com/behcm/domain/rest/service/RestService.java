package com.behcm.domain.rest.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.rest.dto.RestRequest;
import com.behcm.domain.rest.dto.RestResponse;
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

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RestService {
    private final RestRepository restRepository;
    private final WorkoutRoomMemberRepository workoutRoomMemberRepository;

    public RestResponse registerRestDay(Member member, RestRequest request) {
        WorkoutRoomMember workoutRoomMember = workoutRoomMemberRepository.findByMember(member)
                .orElseThrow(() -> new CustomException(ErrorCode.WORKOUT_ROOM_NOT_FOUND, "참여 중인 운동방이 없습니다."));
        
        LocalDate startDate = LocalDate.parse(request.getStartDate());
        LocalDate endDate = LocalDate.parse(request.getEndDate());
        
        Rest rest = Rest.builder()
                .workoutRoomMember(workoutRoomMember)
                .reason(request.getReason())
                .startDate(startDate)
                .endDate(endDate)
                .build();
        
        Rest savedRest = restRepository.save(rest);
        
        return RestResponse.from(savedRest);
    }
}
