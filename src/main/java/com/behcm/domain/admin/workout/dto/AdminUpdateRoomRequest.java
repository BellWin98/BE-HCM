package com.behcm.domain.admin.workout.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AdminUpdateRoomRequest {

    @NotNull(message = "시작일은 필수입니다.")
    private LocalDate startDate;

    @NotNull(message = "종료일은 필수입니다.")
    private LocalDate endDate;

    @NotNull(message = "최대 인원은 필수입니다.")
    @Min(value = 2, message = "최대 인원은 2명 이상이어야 합니다.")
    private Integer maxMembers;

    @NotNull(message = "주당 최소 운동 횟수는 필수입니다.")
    @Min(value = 1, message = "주당 최소 운동 횟수는 1회 이상이어야 합니다.")
    @Max(value = 7, message = "주당 최소 운동 횟수는 7회 이하이어야 합니다.")
    private Integer minWeeklyWorkouts;

    @NotNull(message = "회당 벌금은 필수입니다.")
    @Min(value = 1000, message = "회당 벌금은 1000원 이상이어야 합니다.")
    private Long penaltyPerMiss;
}

