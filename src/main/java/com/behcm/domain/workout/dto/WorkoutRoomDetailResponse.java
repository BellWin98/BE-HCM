package com.behcm.domain.workout.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class WorkoutRoomDetailResponse {
    private WorkoutRoomResponse workoutRoomInfo;
    private List<WorkoutRoomMemberResponse> workoutRoomMembers;
    private WorkoutRecordResponse currentMemberTodayWorkoutRecord;
}
