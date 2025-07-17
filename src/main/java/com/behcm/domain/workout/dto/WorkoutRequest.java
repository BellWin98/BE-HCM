package com.behcm.domain.workout.dto;

import lombok.Builder;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
public class WorkoutRequest {
    private String workoutDate;
    private String workoutType;
    private Integer duration;
    private MultipartFile image;
}