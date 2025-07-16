package com.behcm.domain.workout.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateWorkoutRoomRequest {

    @NotEmpty(message = "방 이름은 필수입니다.")
    private String name;

    @NotNull(message = "주당 최소 운동 횟수는 필수입니다.")
    @Min(value = 1, message = "주당 최소 운동 횟수는 1회 이상이어야 합니다.")
    @Max(value = 7, message = "주당 최소 운동 횟수는 7회 이하이어야 합니다.")
    private Integer minWeeklyWorkouts;

    @NotNull(message = "회당 벌금은 필수입니다.")
    @Min(value = 1000, message = "회당 벌금은 1000원 이상이어야 합니다.")
    private Long penaltyPerMiss;

    @NotNull(message = "시작일은 필수입니다.")
    private LocalDate startDate;
    private LocalDate endDate;

    @NotNull(message = "최대 인원은 필수입니다.")
    @Min(value = 2, message = "최대 인원은 2명 이상이어야 합니다.")
    private Integer maxMembers;

    @NotEmpty(message = "방 비밀번호는 필수입니다.")
    @Size(min = 2, max = 8, message = "방 비밀번호는 2~8자리이어야 합니다.")
    private String entryCode;
}
