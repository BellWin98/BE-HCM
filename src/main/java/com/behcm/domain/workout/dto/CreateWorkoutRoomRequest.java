package com.behcm.domain.workout.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

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

    @NotNull(message = "최대 인원은 필수입니다.")
    @Min(value = 1, message = "최대 인원은 1명 이상이어야 합니다.")
    private Integer maxMembers;

    @NotEmpty(message = "방 입장 코드는 필수입니다.")
    @Size(min = 6, max = 10, message = "방 입장 코드는 6~10자리여야 합니다.")
    @Pattern(regexp = "^[A-Za-z0-9]{6,10}$", message = "방 입장 코드는 영문/숫자 조합만 가능합니다.")
    private String entryCode;
}
