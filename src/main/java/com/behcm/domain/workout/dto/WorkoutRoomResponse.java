package com.behcm.domain.workout.dto;

import com.behcm.domain.workout.entity.WorkoutRoom;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class WorkoutRoomResponse {

    private Long id;
    private String name;
    private String ownerNickname;
    private Integer minWeeklyWorkouts;
    private Boolean penaltyEnabled;
    private Long penaltyPerMiss;
    private Boolean pendingPenaltyEnabled;
    private Long pendingPenaltyPerMiss;
    private LocalDate penaltyChangeEffectiveDate;
    private Integer maxMembers;
    private Integer currentMembers;
    private Boolean isActive;
    private String entryCode;
    private LocalDateTime createdAt;

    public static WorkoutRoomResponse from(WorkoutRoom workoutRoom) {
        return from(workoutRoom, true);
    }

    public static WorkoutRoomResponse fromWithoutEntryCode(WorkoutRoom workoutRoom) {
        return from(workoutRoom, false);
    }

    private static WorkoutRoomResponse from(WorkoutRoom workoutRoom, boolean includeEntryCode) {
        return WorkoutRoomResponse.builder()
                .id(workoutRoom.getId())
                .name(workoutRoom.getName())
                .minWeeklyWorkouts(workoutRoom.getMinWeeklyWorkouts())
                .penaltyEnabled(workoutRoom.getPenaltyEnabled())
                .penaltyPerMiss(workoutRoom.getPenaltyPerMiss())
                .pendingPenaltyEnabled(workoutRoom.getPendingPenaltyEnabled())
                .pendingPenaltyPerMiss(workoutRoom.getPendingPenaltyPerMiss())
                .penaltyChangeEffectiveDate(workoutRoom.getPenaltyChangeEffectiveDate())
                .maxMembers(workoutRoom.getMaxMembers())
                .currentMembers(workoutRoom.getCurrentMemberCount())
                .ownerNickname(workoutRoom.getOwnerNickname())
                .isActive(workoutRoom.getIsActive())
                .entryCode(includeEntryCode ? workoutRoom.getEntryCode() : null)
                .createdAt(workoutRoom.getCreatedAt())
                .build();
    }
}
