package com.behcm.domain.stats.service;

import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.domain.stats.dto.LandingStatsResponse;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.domain.workout.repository.WorkoutRoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private WorkoutRecordRepository workoutRecordRepository;

    @Mock
    private WorkoutRoomRepository workoutRoomRepository;

    @InjectMocks
    private StatsService statsService;

    @Test
    @DisplayName("getLandingStats는 회원수/운동인증수/활성방수를 각 레포지토리에서 집계해 반환한다")
    void getLandingStats_aggregatesCountsFromRepositories() {
        given(memberRepository.count()).willReturn(120L);
        given(workoutRecordRepository.count()).willReturn(4500L);
        given(workoutRoomRepository.countActiveRooms()).willReturn(37L);

        LandingStatsResponse response = statsService.getLandingStats();

        assertThat(response.getTotalUsers()).isEqualTo(120L);
        assertThat(response.getTotalExerciseProofs()).isEqualTo(4500L);
        assertThat(response.getActiveRooms()).isEqualTo(37L);
    }
}
