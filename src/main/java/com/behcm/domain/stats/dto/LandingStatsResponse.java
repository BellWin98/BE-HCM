package com.behcm.domain.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LandingStatsResponse {

    private final long totalUsers;
    private final long totalExerciseProofs;
    private final long activeRooms;

    public static LandingStatsResponse of(long totalUsers, long totalExerciseProofs, long activeRooms) {
        return new LandingStatsResponse(totalUsers, totalExerciseProofs, activeRooms);
    }
}

