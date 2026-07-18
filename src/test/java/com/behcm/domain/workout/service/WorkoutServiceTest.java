package com.behcm.domain.workout.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.domain.workout.dto.WorkoutRequest;
import com.behcm.domain.workout.dto.WorkoutResponse;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import com.behcm.global.config.aws.S3Service;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkoutServiceTest {

    @Mock
    private WorkoutRecordRepository workoutRecordRepository;

    @Mock
    private WorkoutRoomMemberRepository workoutRoomMemberRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private S3Service s3Service;

    @Mock
    private CacheManager cacheManager;

    @InjectMocks
    private WorkoutService workoutService;

    private Member member() {
        return Member.builder()
                .email("user@test.com")
                .nickname("user")
                .role(MemberRole.USER)
                .build();
    }

    private WorkoutRoom room(long id) {
        Member owner = member();
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

    private void setId(Object entity, long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private WorkoutRequest request(String date) {
        return WorkoutRequest.builder()
                .workoutDate(date)
                .workoutTypes(List.of("헬스"))
                .duration(60)
                .images(List.of())
                .build();
    }

    @Test
    @DisplayName("참여 중인 운동방이 없으면 WORKOUT_ROOM_NOT_FOUND 예외를 던진다")
    void authenticateWorkout_noWorkoutRoom_throwsWorkoutRoomNotFound() {
        Member member = member();
        given(workoutRoomMemberRepository.findByMember(member)).willReturn(List.of());

        assertThatThrownBy(() -> workoutService.authenticateWorkout(member, request("2026-07-20")))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WORKOUT_ROOM_NOT_FOUND);

        verify(s3Service, never()).uploadWorkoutImages(any());
    }

    @Test
    @DisplayName("해당 날짜에 이미 인증한 기록이 있으면 WORKOUT_ALREADY_AUTHENTICATED 예외를 던진다")
    void authenticateWorkout_alreadyAuthenticatedForDate_throwsAlreadyAuthenticated() {
        Member member = member();
        WorkoutRoom room = room(1L);
        WorkoutRoomMember wrm = WorkoutRoomMember.builder().member(member).workoutRoom(room).build();
        given(workoutRoomMemberRepository.findByMember(member)).willReturn(List.of(wrm));
        given(workoutRecordRepository.findByMemberAndWorkoutDateAndWorkoutRoomIn(any(), any(), any()))
                .willReturn(List.of(WorkoutRecord.builder().member(member).workoutRoom(room).workoutDate(LocalDate.parse("2026-07-20")).build()));

        assertThatThrownBy(() -> workoutService.authenticateWorkout(member, request("2026-07-20")))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WORKOUT_ALREADY_AUTHENTICATED);

        verify(s3Service, never()).uploadWorkoutImages(any());
    }

    @Test
    @DisplayName("정상 인증 시 참여 중인 모든 방에 운동 기록이 생성되고 총 운동일수가 증가한다")
    void authenticateWorkout_success_recordsToAllRoomsAndIncrementsTotalDays() {
        Member member = member();
        WorkoutRoom room1 = room(1L);
        WorkoutRoom room2 = room(2L);
        WorkoutRoomMember wrm1 = WorkoutRoomMember.builder().member(member).workoutRoom(room1).build();
        WorkoutRoomMember wrm2 = WorkoutRoomMember.builder().member(member).workoutRoom(room2).build();
        given(workoutRoomMemberRepository.findByMember(member)).willReturn(List.of(wrm1, wrm2));
        given(workoutRecordRepository.findByMemberAndWorkoutDateAndWorkoutRoomIn(any(), any(), any()))
                .willReturn(List.of());
        given(s3Service.uploadWorkoutImages(any())).willReturn(List.of("https://s3/image.jpg"));
        given(workoutRecordRepository.save(any(WorkoutRecord.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

        String today = LocalDate.now().toString();
        WorkoutResponse response = workoutService.authenticateWorkout(member, request(today));

        assertThat(wrm1.getTotalWorkouts()).isEqualTo(1);
        assertThat(wrm2.getTotalWorkouts()).isEqualTo(1);
        assertThat(wrm1.getWeeklyWorkouts()).isEqualTo(1);
        assertThat(member.getTotalWorkoutDays()).isEqualTo(1);
        assertThat(response.getImageUrls()).containsExactly("https://s3/image.jpg");
        verify(workoutRecordRepository, org.mockito.Mockito.times(2)).save(any(WorkoutRecord.class));
    }

    @Test
    @DisplayName("이번 주가 아닌 과거 날짜로 인증하면 주간 운동 횟수는 증가하지 않는다")
    void authenticateWorkout_pastWeekDate_doesNotIncrementWeeklyWorkouts() {
        Member member = member();
        WorkoutRoom room = room(1L);
        WorkoutRoomMember wrm = WorkoutRoomMember.builder().member(member).workoutRoom(room).build();
        given(workoutRoomMemberRepository.findByMember(member)).willReturn(List.of(wrm));
        given(workoutRecordRepository.findByMemberAndWorkoutDateAndWorkoutRoomIn(any(), any(), any()))
                .willReturn(List.of());
        given(s3Service.uploadWorkoutImages(any())).willReturn(List.of("https://s3/image.jpg"));
        given(workoutRecordRepository.save(any(WorkoutRecord.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

        String threeWeeksAgo = LocalDate.now().minusWeeks(3).toString();
        workoutService.authenticateWorkout(member, request(threeWeeksAgo));

        assertThat(wrm.getTotalWorkouts()).isEqualTo(1);
        assertThat(wrm.getWeeklyWorkouts()).isEqualTo(0);
    }
}
