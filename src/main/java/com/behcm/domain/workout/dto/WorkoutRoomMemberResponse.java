package com.behcm.domain.workout.dto;

import com.behcm.domain.rest.dto.RestResponse;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class WorkoutRoomMemberResponse {
    private Long id;
    private String nickname;
    private String profileUrl;
    private Integer totalWorkouts;
    private Integer weeklyWorkouts;
    private Long totalPenalty;
    private Boolean isOnBreak;
    private LocalDateTime joinedAt;
    private List<WorkoutRecordResponse> workoutRecords;
    private List<RestResponse> restInfoList;

    public static WorkoutRoomMemberResponse of(
            WorkoutRoomMember workoutRoomMember,
            List<WorkoutRecordResponse> workoutRecords,
            List<RestResponse> restInfoList
    ) {
        return WorkoutRoomMemberResponse.builder()
                .id(workoutRoomMember.getId())
                .nickname(workoutRoomMember.getNickname())
                .profileUrl(workoutRoomMember.getMember().getProfileUrl())
                .totalWorkouts(workoutRoomMember.getTotalWorkouts())
                .weeklyWorkouts(workoutRoomMember.getWeeklyWorkouts())
                .totalPenalty(workoutRoomMember.getTotalPenalty())
                .isOnBreak(workoutRoomMember.getIsOnBreak())
                .joinedAt(workoutRoomMember.getJoinedAt())
                .workoutRecords(workoutRecords)
                .restInfoList(restInfoList)
                .build();
    }
}
