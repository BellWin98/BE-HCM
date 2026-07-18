package com.behcm.domain.rest.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.rest.dto.RestRequest;
import com.behcm.domain.rest.entity.Rest;
import com.behcm.domain.rest.repository.RestRepository;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RestServiceTest {

    @Mock
    private RestRepository restRepository;

    @Mock
    private WorkoutRoomMemberRepository workoutRoomMemberRepository;

    @InjectMocks
    private RestService restService;

    private Member member() {
        return Member.builder()
                .email("user@test.com")
                .nickname("user")
                .role(MemberRole.USER)
                .build();
    }

    private WorkoutRoomMember wrm(Member member) {
        WorkoutRoom room = WorkoutRoom.builder()
                .name("Test Room")
                .minWeeklyWorkouts(3)
                .penaltyEnabled(false)
                .maxMembers(10)
                .entryCode("ENTRY01")
                .owner(member)
                .build();
        return WorkoutRoomMember.builder().member(member).workoutRoom(room).build();
    }

    private RestRequest request(String start, String end) {
        RestRequest request = new RestRequest();
        request.setReason("여행");
        request.setStartDate(start);
        request.setEndDate(end);
        return request;
    }

    @Test
    @DisplayName("참여 중인 운동방이 없으면 WORKOUT_ROOM_NOT_FOUND 예외를 던진다")
    void registerRestDay_noWorkoutRoom_throwsWorkoutRoomNotFound() {
        Member member = member();
        given(workoutRoomMemberRepository.findByMember(member)).willReturn(List.of());

        assertThatThrownBy(() -> restService.registerRestDay(member, request("2026-08-01", "2026-08-05")))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WORKOUT_ROOM_NOT_FOUND);

        verify(restRepository, never()).save(any());
    }

    @Test
    @DisplayName("기존 휴식 기간과 겹치면 REST_PERIOD_OVERLAP 예외를 던진다")
    void registerRestDay_overlappingPeriod_throwsRestPeriodOverlap() {
        Member member = member();
        WorkoutRoomMember wrm = wrm(member);
        given(workoutRoomMemberRepository.findByMember(member)).willReturn(List.of(wrm));
        Rest existing = Rest.builder()
                .workoutRoomMember(wrm)
                .reason("병가")
                .startDate(java.time.LocalDate.parse("2026-08-03"))
                .endDate(java.time.LocalDate.parse("2026-08-10"))
                .build();
        given(restRepository.findAllByWorkoutRoomMemberIn(List.of(wrm))).willReturn(List.of(existing));

        assertThatThrownBy(() -> restService.registerRestDay(member, request("2026-08-01", "2026-08-05")))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REST_PERIOD_OVERLAP);

        verify(restRepository, never()).save(any());
    }

    @Test
    @DisplayName("겹치지 않는 기간이면 참여 중인 모든 방에 휴식일이 등록된다")
    void registerRestDay_nonOverlappingPeriod_savesForEveryRoom() {
        Member member = member();
        WorkoutRoomMember wrm1 = wrm(member);
        WorkoutRoomMember wrm2 = wrm(member);
        given(workoutRoomMemberRepository.findByMember(member)).willReturn(List.of(wrm1, wrm2));
        given(restRepository.findAllByWorkoutRoomMemberIn(List.of(wrm1, wrm2))).willReturn(List.of());

        restService.registerRestDay(member, request("2026-08-01", "2026-08-05"));

        verify(restRepository, org.mockito.Mockito.times(2)).save(any(Rest.class));
    }

    @Test
    @DisplayName("경계값이 맞닿으면(기존 종료일=신규 시작일) 겹침으로 간주해 REST_PERIOD_OVERLAP 예외를 던진다")
    void registerRestDay_touchingBoundary_isTreatedAsOverlap() {
        Member member = member();
        WorkoutRoomMember wrm = wrm(member);
        given(workoutRoomMemberRepository.findByMember(member)).willReturn(List.of(wrm));
        Rest existing = Rest.builder()
                .workoutRoomMember(wrm)
                .reason("병가")
                .startDate(java.time.LocalDate.parse("2026-08-06"))
                .endDate(java.time.LocalDate.parse("2026-08-10"))
                .build();
        given(restRepository.findAllByWorkoutRoomMemberIn(List.of(wrm))).willReturn(List.of(existing));

        assertThatThrownBy(() -> restService.registerRestDay(member, request("2026-08-01", "2026-08-06")))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REST_PERIOD_OVERLAP);
    }
}
