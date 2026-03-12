package com.behcm.domain.member.service;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.domain.member.repository.MemberSettingsRepository;
import com.behcm.domain.workout.dto.WorkoutFeedItemResponse;
import com.behcm.domain.workout.enums.PeriodType;
import com.behcm.domain.workout.entity.WorkoutRecord;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.global.config.aws.S3Service;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

        WorkoutRecord record = WorkoutRecord.builder()
                .member(member)
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

        WorkoutRecord record = WorkoutRecord.builder()
                .member(member)
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
}

