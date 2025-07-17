package com.behcm.domain.workout.dto;

import com.behcm.domain.rest.dto.RestInfoResponse;
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
    private List<RestInfoResponse> restInfoList;

    public static WorkoutRoomMemberResponse of(
            WorkoutRoomMember workoutRoomMember,
            List<WorkoutRecordResponse> workoutRecords,
            List<RestInfoResponse> restInfoList
    ) {
        return WorkoutRoomMemberResponse.builder()
                .id(workoutRoomMember.getId())
                .nickname(workoutRoomMember.getNickname())
                .profileUrl(workoutRoomMember.getMember().getProfileUrl())
                .weeklyWorkouts(workoutRoomMember.getTotalWorkouts())
                .weeklyWorkouts(workoutRoomMember.getWeeklyWorkouts())
                .totalPenalty(workoutRoomMember.getTotalPenalty())
                .isOnBreak(workoutRoomMember.getIsOnBreak())
                .joinedAt(workoutRoomMember.getJoinedAt())
                .workoutRecords(workoutRecords)
                .restInfoList(restInfoList)
                .build();
    }
}
