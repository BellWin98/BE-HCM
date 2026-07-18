package com.behcm.domain.admin.workout.service;

import com.behcm.domain.admin.workout.dto.AdminUpdateRoomRequest;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.rest.dto.RestResponse;
import com.behcm.domain.rest.entity.Rest;
import com.behcm.domain.rest.repository.RestRepository;
import com.behcm.domain.workout.dto.WorkoutRecordResponse;
import com.behcm.domain.workout.dto.WorkoutRoomDetailResponse;
import com.behcm.domain.workout.dto.WorkoutRoomMemberResponse;
import com.behcm.domain.workout.dto.WorkoutRoomResponse;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminWorkoutRoomServiceTest {

    @Mock
    private WorkoutRoomRepository workoutRoomRepository;

    @Mock
    private WorkoutRoomMemberRepository workoutRoomMemberRepository;

    @Mock
    private WorkoutRecordRepository workoutRecordRepository;

    @Mock
    private RestRepository restRepository;

    @InjectMocks
    private AdminWorkoutRoomService adminWorkoutRoomService;

    // WorkoutRoom.owner/WorkoutRoomMember.member는 실제로는 nullable=false라, 응답 DTO 매핑 시
    // owner.getNickname() 등을 그대로 호출한다. 테스트에서 owner(null)로 두면 NPE가 나므로 실제 Member를 준다.
    private Member member() {
        return Member.builder()
                .email("owner@test.com")
                .nickname("owner")
                .role(MemberRole.USER)
                .build();
    }

    @Test
    @DisplayName("getRooms는 레포지토리에서 조회한 WorkoutRoom 페이지를 WorkoutRoomResponse 페이지로 매핑한다")
    void getRooms_mapsToWorkoutRoomResponsePage() {
        Pageable pageable = PageRequest.of(0, 10);
        WorkoutRoom room = WorkoutRoom.builder()
                .name("Room 1")
                .minWeeklyWorkouts(3)
                .penaltyPerMiss(1000L)
                .maxMembers(10)
                .entryCode("ENTRY01")
                .owner(member())
                .build();

        Page<WorkoutRoom> roomPage = new PageImpl<>(List.of(room), pageable, 1);
        given(workoutRoomRepository.searchAdminRooms(eq("query"), eq(true), eq(pageable)))
                .willReturn(roomPage);

        Page<WorkoutRoomResponse> result =
                adminWorkoutRoomService.getRooms("query", true, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        WorkoutRoomResponse response = result.getContent().getFirst();
        assertThat(response.getName()).isEqualTo(room.getName());
        assertThat(response.getMinWeeklyWorkouts()).isEqualTo(room.getMinWeeklyWorkouts());

        verify(workoutRoomRepository).searchAdminRooms("query", true, pageable);
    }

    @Test
    @DisplayName("getRooms 호출 시 공백 query는 null로 정규화되어 레포지토리에 전달된다")
    void getRooms_normalizesBlankQueryToNull() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<WorkoutRoom> emptyPage = Page.empty(pageable);
        given(workoutRoomRepository.searchAdminRooms(isNull(), eq(null), eq(pageable)))
                .willReturn(emptyPage);

        Page<WorkoutRoomResponse> result =
                adminWorkoutRoomService.getRooms("   ", null, pageable);

        assertThat(result.getTotalElements()).isZero();
        verify(workoutRoomRepository).searchAdminRooms(null, null, pageable);
    }

    @Test
    @DisplayName("getRoomDetail은 운동방이 존재하지 않으면 CustomException(WORKOUT_ROOM_NOT_FOUND)을 던진다")
    void getRoomDetail_whenRoomNotFound_throwsCustomException() {
        given(workoutRoomRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminWorkoutRoomService.getRoomDetail(1L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WORKOUT_ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("getRoomDetail은 운동방, 멤버, 운동 기록, 휴식 정보를 조회해 WorkoutRoomDetailResponse로 반환한다")
    void getRoomDetail_returnsAggregatedDetailResponse() {
        WorkoutRoom room = WorkoutRoom.builder()
                .name("Room 1")
                .minWeeklyWorkouts(3)
                .penaltyPerMiss(1000L)
                .maxMembers(10)
                .entryCode("ENTRY01")
                .owner(member())
                .build();

        WorkoutRoomMember workoutRoomMember = WorkoutRoomMember.builder()
                .member(member())
                .workoutRoom(room)
                .build();

        WorkoutRecord workoutRecord = WorkoutRecord.builder()
                .workoutDate(java.time.LocalDate.now())
                .duration(30)
                .build();

        Rest rest = Rest.builder()
                .reason("휴식")
                .startDate(java.time.LocalDate.now())
                .endDate(java.time.LocalDate.now().plusDays(1))
                .build();

        given(workoutRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAt(room))
                .willReturn(List.of(workoutRoomMember));
        given(workoutRecordRepository.findAllByMemberPerWorkoutDate(workoutRoomMember.getMember()))
                .willReturn(List.of(workoutRecord));
        given(restRepository.findAllByWorkoutRoomMember(workoutRoomMember))
                .willReturn(List.of(rest));

        WorkoutRoomDetailResponse result = adminWorkoutRoomService.getRoomDetail(1L);

        assertThat(result.getWorkoutRoomInfo().getName()).isEqualTo(room.getName());
        assertThat(result.getWorkoutRoomMembers()).hasSize(1);
        WorkoutRoomMemberResponse memberResponse = result.getWorkoutRoomMembers().getFirst();
        List<WorkoutRecordResponse> workoutRecords = memberResponse.getWorkoutRecords();
        List<RestResponse> restInfoList = memberResponse.getRestInfoList();

        assertThat(workoutRecords).hasSize(1);
        assertThat(workoutRecords.getFirst().getDuration()).isEqualTo(30);
        assertThat(restInfoList).hasSize(1);
        assertThat(restInfoList.getFirst().getReason()).isEqualTo("휴식");
        assertThat(result.getCurrentMemberTodayWorkoutRecord()).isNull();
    }

    @Test
    @DisplayName("updateRoomSettings는 유효한 요청에 대해 운동방 설정을 변경하고 저장된 결과를 반환한다")
    void updateRoomSettings_updatesRoomAndReturnsResponse() {
        WorkoutRoom room = WorkoutRoom.builder()
                .name("Room 1")
                .minWeeklyWorkouts(3)
                .penaltyPerMiss(1000L)
                .maxMembers(10)
                .entryCode("ENTRY01")
                .owner(member())
                .build();

        AdminUpdateRoomRequest request = new AdminUpdateRoomRequest();
        request.setMaxMembers(20);
        request.setMinWeeklyWorkouts(5);
        request.setPenaltyPerMiss(2000L);

        given(workoutRoomRepository.findById(1L)).willReturn(Optional.of(room));
        given(workoutRoomRepository.save(room)).willReturn(room);

        WorkoutRoomResponse response = adminWorkoutRoomService.updateRoomSettings(1L, request);

        assertThat(response.getMinWeeklyWorkouts()).isEqualTo(5);
        assertThat(response.getPenaltyPerMiss()).isEqualTo(2000L);
        assertThat(response.getMaxMembers()).isEqualTo(20);

        verify(workoutRoomRepository).findById(1L);
        verify(workoutRoomRepository).save(room);
    }
}

