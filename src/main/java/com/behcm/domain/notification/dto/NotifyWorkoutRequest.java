package com.behcm.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class NotifyWorkoutRequest {

    @NotBlank(message = "운동 날짜는 필수입니다")
    private String workoutDate;

    @NotNull(message = "운동 시간은 필수입니다")
    private Integer duration;

    @NotEmpty(message = "운동 종류는 최소 1개 이상이어야 합니다")
    private List<String> types;

    public NotifyWorkoutRequest(String workoutDate, Integer duration, List<String> types) {
        this.workoutDate = workoutDate;
        this.duration = duration;
        this.types = types;
    }
}