package com.behcm.domain.workout.dto;

import com.behcm.domain.workout.entity.WorkoutRoom;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WorkoutRoomResponse {

    private Long id;
    private String name;
    private String ownerNickname;
    private Integer minWeeklyWorkouts;
    private Long penaltyPerMiss;
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
                .penaltyPerMiss(workoutRoom.getPenaltyPerMiss())
                .maxMembers(workoutRoom.getMaxMembers())
                .currentMembers(workoutRoom.getCurrentMemberCount())
                .ownerNickname(workoutRoom.getOwnerNickname())
                .isActive(workoutRoom.getIsActive())
                .entryCode(includeEntryCode ? workoutRoom.getEntryCode() : null)
                .createdAt(workoutRoom.getCreatedAt())
                .build();
    }
}
