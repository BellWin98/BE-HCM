package com.behcm.domain.workout.dto;

import com.behcm.domain.member.dto.MemberResponse;
import com.behcm.domain.workout.entity.WorkoutRoom;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class WorkoutRoomResponse {

    private Long id;
    private String name;
    private Integer minWeeklyWorkouts; // 1주당 최소 운동 인증 횟수
    private Long penaltyPerMiss;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer maxMembers;
    private Integer currentMembers;
    private String entryCode;
    private MemberResponse owner;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private List<WorkoutRoomMemberResponse> members;

    public static WorkoutRoomResponse from(WorkoutRoom workoutRoom) {
        return WorkoutRoomResponse.builder()
                .id(workoutRoom.getId())
                .name(workoutRoom.getName())
                .minWeeklyWorkouts(workoutRoom.getMinWeeklyWorkouts())
                .penaltyPerMiss(workoutRoom.getPenaltyPerMiss())
                .startDate(workoutRoom.getStartDate())
                .endDate(workoutRoom.getEndDate())
                .maxMembers(workoutRoom.getMaxMembers())
                .currentMembers(workoutRoom.getCurrentMemberCount())
                .entryCode(workoutRoom.getEntryCode())
                .owner(MemberResponse.from(workoutRoom.getOwner()))
                .isActive(workoutRoom.getIsActive())
                .createdAt(workoutRoom.getCreatedAt())
                .build();
    }

    public static WorkoutRoomResponse from(WorkoutRoom workoutRoom, List<WorkoutRoomMemberResponse> members) {
        return WorkoutRoomResponse.builder()
                .id(workoutRoom.getId())
                .name(workoutRoom.getName())
                .minWeeklyWorkouts(workoutRoom.getMinWeeklyWorkouts())
                .penaltyPerMiss(workoutRoom.getPenaltyPerMiss())
                .startDate(workoutRoom.getStartDate())
                .endDate(workoutRoom.getEndDate())
                .maxMembers(workoutRoom.getMaxMembers())
                .currentMembers(workoutRoom.getCurrentMemberCount())
                .owner(MemberResponse.from(workoutRoom.getOwner()))
                .isActive(workoutRoom.getIsActive())
                .createdAt(workoutRoom.getCreatedAt())
                .members(members)
                .build();
    }
}
