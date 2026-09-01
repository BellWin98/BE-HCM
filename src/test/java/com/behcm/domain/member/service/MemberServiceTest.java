package com.behcm.domain.member.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.domain.member.repository.MemberSettingsRepository;
import com.behcm.domain.social.dto.GroupedSocialSummary;
import com.behcm.domain.social.dto.ReactionCountResponse;
import com.behcm.domain.social.dto.WorkoutSocialSummary;
import com.behcm.domain.social.entity.ReactionEmoji;
import com.behcm.domain.social.service.WorkoutSocialQueryService;
import com.behcm.domain.workout.dto.WorkoutFeedItemResponse;
import com.behcm.domain.workout.enums.PeriodType;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.global.config.aws.S3Service;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private WorkoutRecordRepository workoutRecordRepository;

    @Mock
    private MemberSettingsRepository memberSettingsRepository;

    @Mock
    private S3Service s3Service;

    @Mock
    private WorkoutSocialQueryService workoutSocialQueryService;

    @InjectMocks
    private MemberService memberService;

    @Test
    @DisplayName("getMemberWorkoutFeed는 periodType이 ALL이거나 null이면 전체 기간을 조회한다")
    void getMemberWorkoutFeed_allPeriod() {
        // given
        Member member = Member.builder()
                .email("test@example.com")
                .password("password")
                .nickname("tester")
                .build();
        int page = 0;
        int size = 10;
        Pageable pageable = PageRequest.of(page, size);

        // WorkoutFeedItemResponse.from은 workoutRecord.getWorkoutRoom().getName()을 그대로 호출하므로
        // workoutRoom이 없으면 NPE가 난다. 실제로는 nullable=false라 항상 존재하는 값이다.
        WorkoutRoom room = WorkoutRoom.builder()
                .name("Test Room")
                .minWeeklyWorkouts(3)
                .penaltyEnabled(false)
                .maxMembers(10)
                .entryCode("ENTRY01")
                .owner(member)
                .build();

        WorkoutRecord record = WorkoutRecord.builder()
                .member(member)
                .workoutRoom(room)
                .workoutDate(LocalDate.now())
                .duration(30)
                .build();
        Page<WorkoutRecord> recordPage = new PageImpl<>(List.of(record), pageable, 1);

        given(workoutRecordRepository.findAllByMemberPerWorkoutDate(member, pageable)).willReturn(recordPage);

        // when
        Page<WorkoutFeedItemResponse> result = memberService.getMemberWorkoutFeed(member, page, size, PeriodType.ALL);

        // then
        assertThat(result.getContent()).hasSize(1);
        verify(workoutRecordRepository).findAllByMemberPerWorkoutDate(member, pageable);
    }

    @Test
    @DisplayName("getMemberWorkoutFeed는 periodType이 WEEK/MONTH이면 해당 기간만 조회한다")
    void getMemberWorkoutFeed_weekAndMonthPeriod() {
        // given
        Member member = Member.builder()
                .email("test2@example.com")
                .password("password")
                .nickname("tester2")
                .build();
        int page = 0;
        int size = 10;
        Pageable pageable = PageRequest.of(page, size);

        WorkoutRoom room = WorkoutRoom.builder()
                .name("Test Room")
                .minWeeklyWorkouts(3)
                .penaltyEnabled(false)
                .maxMembers(10)
                .entryCode("ENTRY02")
                .owner(member)
                .build();

        WorkoutRecord record = WorkoutRecord.builder()
                .member(member)
                .workoutRoom(room)
                .workoutDate(LocalDate.now())
                .duration(30)
                .build();
        Page<WorkoutRecord> recordPage = new PageImpl<>(List.of(record), pageable, 1);

        given(workoutRecordRepository.findAllByMemberPerWorkoutDateAndWorkoutDateBetween(
                org.mockito.Mockito.eq(member),
                org.mockito.Mockito.any(LocalDate.class),
                org.mockito.Mockito.any(LocalDate.class),
                org.mockito.Mockito.eq(pageable)
        )).willReturn(recordPage);

        // when
        Page<WorkoutFeedItemResponse> weekResult = memberService.getMemberWorkoutFeed(member, page, size, PeriodType.WEEK);
        Page<WorkoutFeedItemResponse> monthResult = memberService.getMemberWorkoutFeed(member, page, size, PeriodType.MONTH);

        // then
        assertThat(weekResult.getContent()).hasSize(1);
        assertThat(monthResult.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getMemberWorkoutFeed는 같은 날 여러 방에 저장된 인증의 리액션/댓글을 합쳐서 보여준다")
    void getMemberWorkoutFeed_aggregatesSocialAcrossRooms() {
        // given: 두 방에 참여 중이라 하루 인증이 방마다 한 건씩(총 2건) 저장돼 있고,
        // 피드에는 그중 대표 한 건(2번 방 레코드)만 올라온다.
        Member member = Member.builder()
                .email("multi@example.com")
                .password("password")
                .nickname("multi")
                .build();
        ReflectionTestUtils.setField(member, "id", 1L);

        LocalDate workoutDate = LocalDate.of(2026, 8, 30);
        WorkoutRoom roomB = room(member, "B방", "ENTRY04");
        WorkoutRecord representative = WorkoutRecord.builder()
                .member(member)
                .workoutRoom(roomB)
                .workoutDate(workoutDate)
                .duration(30)
                .build();
        ReflectionTestUtils.setField(representative, "id", 11L);

        Pageable pageable = PageRequest.of(0, 10);
        given(workoutRecordRepository.findAllByMemberPerWorkoutDate(member, pageable))
                .willReturn(new PageImpl<>(List.of(representative), pageable, 1));

        // (workoutDate, recordId, roomId, roomName)
        given(workoutRecordRepository.findRoomBreakdownByMemberAndWorkoutDateIn(eq(member), anyCollection()))
                .willReturn(List.<Object[]>of(
                        new Object[]{workoutDate, 10L, 100L, "A방"},
                        new Object[]{workoutDate, 11L, 200L, "B방"}
                ));

        // A방(10L)에만 리액션 2개와 댓글 1개가 달린 상황
        WorkoutSocialSummary roomA = WorkoutSocialSummary.builder()
                .reactions(List.of(ReactionCountResponse.of(ReactionEmoji.MUSCLE, 2L, false)))
                .commentCount(1L)
                .build();
        given(workoutSocialQueryService.summarizeGrouped(anyMap(), eq(member)))
                .willReturn(Map.of(11L, GroupedSocialSummary.builder()
                        .total(roomA)
                        .byRecordId(Map.of(10L, roomA, 11L, WorkoutSocialSummary.empty()))
                        .build()));

        // when
        Page<WorkoutFeedItemResponse> result = memberService.getMemberWorkoutFeed(member, 0, 10, PeriodType.ALL);

        // then: 대표 레코드에는 반응이 없지만 A방 반응이 합계로 잡혀야 한다
        WorkoutFeedItemResponse item = result.getContent().getFirst();
        assertThat(item.getCommentCount()).isEqualTo(1L);
        assertThat(item.getReactions())
                .extracting(ReactionCountResponse::getEmoji, ReactionCountResponse::getCount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("MUSCLE", 2L));

        // 방별 내역도 함께 내려간다
        assertThat(item.getRooms())
                .extracting(r -> r.getRoomName(), r -> r.getRecordId(), r -> r.getCommentCount())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("A방", 10L, 1L),
                        org.assertj.core.groups.Tuple.tuple("B방", 11L, 0L)
                );

        // 집계 요청에는 그날의 형제 레코드가 모두 실려야 한다
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Long, Collection<Long>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(workoutSocialQueryService).summarizeGrouped(captor.capture(), eq(member));
        assertThat(captor.getValue()).containsOnlyKeys(11L);
        assertThat(captor.getValue().get(11L)).containsExactlyInAnyOrder(10L, 11L);
    }

    private WorkoutRoom room(Member owner, String name, String entryCode) {
        return WorkoutRoom.builder()
                .name(name)
                .minWeeklyWorkouts(3)
                .penaltyEnabled(false)
                .maxMembers(10)
                .entryCode(entryCode)
                .owner(owner)
                .build();
    }
}
