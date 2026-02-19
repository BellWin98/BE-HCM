package com.behcm.domain.workout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class JoinWorkoutRoomByCodeRequest {

    @NotBlank(message = "방 입장 코드는 필수입니다.")
    @Size(min = 6, max = 10, message = "방 입장 코드는 6~10자리여야 합니다.")
    @Pattern(regexp = "^[A-Za-z0-9]{6,10}$", message = "방 입장 코드는 영문/숫자 조합만 가능합니다.")
    private String entryCode;
}

