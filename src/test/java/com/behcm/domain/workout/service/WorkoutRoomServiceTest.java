package com.behcm.domain.workout.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.rest.repository.RestRepository;
import com.behcm.domain.workout.dto.SchedulePenaltyChangeRequest;
import com.behcm.domain.workout.dto.WorkoutRoomResponse;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkoutRoomServiceTest {

    @Mock
    private WorkoutRoomRepository workoutRoomRepository;

    @Mock
    private WorkoutRoomMemberRepository workoutRoomMemberRepository;

    @Mock
    private WorkoutRecordRepository workoutRecordRepository;

    @Mock
    private RestRepository restRepository;

    @InjectMocks
    private WorkoutRoomService workoutRoomService;

    private static LocalDate earliestAllowedMonday() {
        return LocalDate.now().plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
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

    // Member.id has no public setter; use reflection so the test can assert ownership without a DB.
    private void setId(Object entity, long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private WorkoutRoom room(Member owner, boolean penaltyEnabled, Long penaltyPerMiss) {
        WorkoutRoom room = WorkoutRoom.builder()
                .name("Test Room")
                .minWeeklyWorkouts(3)
                .penaltyEnabled(penaltyEnabled)
                .penaltyPerMiss(penaltyPerMiss)
                .maxMembers(10)
                .entryCode("ENTRY01")
                .owner(owner)
                .build();
        setId(room, 1L);
        return room;
    }

    @Test
    @DisplayName("방장이 최소 7일 이후의 월요일로 예약하면 pending 필드가 설정된다")
    void scheduleOwnerPenaltyChange_ownerSchedulesValidMonday_updatesPendingFields() {
        Member owner = member(1L);
        WorkoutRoom room = room(owner, true, 5000L);
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(room));

        SchedulePenaltyChangeRequest request = new SchedulePenaltyChangeRequest();
        request.setPenaltyEnabled(false);
        request.setEffectiveDate(earliestAllowedMonday());

        WorkoutRoomResponse response = workoutRoomService.scheduleOwnerPenaltyChange(1L, request, owner);

        assertThat(response.getPendingPenaltyEnabled()).isFalse();
        assertThat(response.getPenaltyChangeEffectiveDate()).isEqualTo(earliestAllowedMonday());
        assertThat(response.getPenaltyEnabled()).isTrue(); // 즉시 반영되지 않음
    }

    @Test
    @DisplayName("방장이 아닌 멤버가 예약을 시도하면 NOT_WORKOUT_ROOM_OWNER 예외가 발생한다")
    void scheduleOwnerPenaltyChange_nonOwner_throwsNotWorkoutRoomOwner() {
        Member owner = member(1L);
        Member other = member(2L);
        WorkoutRoom room = room(owner, true, 5000L);
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(room));

        SchedulePenaltyChangeRequest request = new SchedulePenaltyChangeRequest();
        request.setPenaltyEnabled(false);
        request.setEffectiveDate(earliestAllowedMonday());

        assertThatThrownBy(() -> workoutRoomService.scheduleOwnerPenaltyChange(1L, request, other))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_WORKOUT_ROOM_OWNER);
    }

    @Test
    @DisplayName("월요일이 아닌 날짜로 예약하면 INVALID_PENALTY_EFFECTIVE_DATE 예외가 발생한다")
    void scheduleOwnerPenaltyChange_nonMonday_throwsInvalidEffectiveDate() {
        Member owner = member(1L);
        WorkoutRoom room = room(owner, true, 5000L);
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(room));

        SchedulePenaltyChangeRequest request = new SchedulePenaltyChangeRequest();
        request.setPenaltyEnabled(false);
        request.setEffectiveDate(earliestAllowedMonday().plusDays(1)); // 화요일

        assertThatThrownBy(() -> workoutRoomService.scheduleOwnerPenaltyChange(1L, request, owner))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PENALTY_EFFECTIVE_DATE);
    }

    @Test
    @DisplayName("월요일이지만 오늘로부터 7일 미만이면 INVALID_PENALTY_EFFECTIVE_DATE 예외가 발생한다")
    void scheduleOwnerPenaltyChange_mondayTooSoon_throwsInvalidEffectiveDate() {
        Member owner = member(1L);
        WorkoutRoom room = room(owner, true, 5000L);
        given(workoutRoomRepository.findByIdAndIsActiveTrue(1L)).willReturn(Optional.of(room));

        SchedulePenaltyChangeRequest request = new SchedulePenaltyChangeRequest();
        request.setPenaltyEnabled(false);
        request.setEffectiveDate(earliestAllowedMonday().minusWeeks(1)); // 한 주 이른 월요일

        assertThatThrownBy(() -> workoutRoomService.scheduleOwnerPenaltyChange(1L, request, owner))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PENALTY_EFFECTIVE_DATE);
    }

    @Test
    @DisplayName("관리자는 방장 여부와 무관하게 예약할 수 있다")
    void scheduleAdminPenaltyChange_doesNotRequireOwnership() {
        Member owner = member(1L);
        WorkoutRoom room = room(owner, true, 5000L);
        given(workoutRoomRepository.findById(1L)).willReturn(Optional.of(room));

        SchedulePenaltyChangeRequest request = new SchedulePenaltyChangeRequest();
        request.setPenaltyEnabled(false);
        request.setEffectiveDate(earliestAllowedMonday());

        WorkoutRoomResponse response = workoutRoomService.scheduleAdminPenaltyChange(1L, request);

        assertThat(response.getPendingPenaltyEnabled()).isFalse();
    }

    @Test
    @DisplayName("applyDuePendingPenaltyChanges는 예약 도래일이 지난 방에만 pending 전환을 적용한다")
    void applyDuePendingPenaltyChanges_appliesOnlyToDueRooms() {
        Member owner = member(1L);
        WorkoutRoom dueRoom = room(owner, true, 5000L);
        dueRoom.schedulePenaltyChange(false, null, LocalDate.now());

        given(workoutRoomRepository.findByIsActiveTrueAndPenaltyChangeEffectiveDateLessThanEqual(any(LocalDate.class)))
                .willReturn(List.of(dueRoom));

        workoutRoomService.applyDuePendingPenaltyChanges();

        assertThat(dueRoom.getPenaltyEnabled()).isFalse();
        assertThat(dueRoom.getPendingPenaltyEnabled()).isNull();
    }

    @Test
    @DisplayName("getWorkoutRooms는 활성 운동방 목록을 owner가 fetch-join된 상태로 응답으로 변환한다")
    void getWorkoutRooms_mapsActiveRoomsToResponses() {
        Member owner = member(1L);
        WorkoutRoom room = room(owner, true, 5000L);
        given(workoutRoomRepository.findActiveRooms()).willReturn(List.of(room));

        List<WorkoutRoomResponse> result = workoutRoomService.getWorkoutRooms();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getOwnerNickname()).isEqualTo(owner.getNickname());
    }

    @Test
    @DisplayName("getJoinedWorkoutRooms는 fetch-join 배치 쿼리로 가입한 운동방 목록을 응답으로 변환한다")
    void getJoinedWorkoutRooms_returnsMappedResponses() {
        Member owner = member(1L);
        Member member = member(2L);
        WorkoutRoom room = room(owner, true, 5000L);
        WorkoutRoomMember workoutRoomMember = WorkoutRoomMember.builder().member(member).workoutRoom(room).build();

        given(workoutRoomMemberRepository.findByMemberFetchWorkoutRoomAndOwner(member))
                .willReturn(List.of(workoutRoomMember));

        List<WorkoutRoomResponse> result = workoutRoomService.getJoinedWorkoutRooms(member);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getOwnerNickname()).isEqualTo(owner.getNickname());
        verify(workoutRoomMemberRepository, never()).findByMember(any());
    }
}
