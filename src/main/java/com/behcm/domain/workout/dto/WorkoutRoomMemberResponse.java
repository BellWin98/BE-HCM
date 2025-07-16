package com.behcm.domain.workout.dto;

import com.behcm.domain.member.dto.MemberResponse;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WorkoutRoomMemberResponse {

    private Long id;
    private MemberResponse member;
    private Integer weeklyWorkouts;
    private Long totalPenalty;
    private Boolean isOnBreak;
    private LocalDateTime joinedAt;

    public static WorkoutRoomMemberResponse from(WorkoutRoomMember workoutRoomMember) {
        return WorkoutRoomMemberResponse.builder()
                .id(workoutRoomMember.getId())
                .member(MemberResponse.from(workoutRoomMember.getMember()))
                .weeklyWorkouts(workoutRoomMember.getWeeklyWorkouts())
                .totalPenalty(workoutRoomMember.getTotalPenalty())
                .isOnBreak(workoutRoomMember.getIsOnBreak())
                .joinedAt(workoutRoomMember.getJoinedAt())
                .build();
    }
}
