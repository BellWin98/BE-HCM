package com.behcm.domain.stats.service;

import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.domain.stats.dto.LandingStatsResponse;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.domain.workout.repository.WorkoutRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final MemberRepository memberRepository;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final WorkoutRoomRepository workoutRoomRepository;

    public LandingStatsResponse getLandingStats() {
        long totalUsers = memberRepository.count();
        long totalExerciseProofs = workoutRecordRepository.count();
        long activeRooms = workoutRoomRepository.countActiveRooms();

        return LandingStatsResponse.of(totalUsers, totalExerciseProofs, activeRooms);
    }
}

