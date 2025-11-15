package com.behcm.domain.workout.dto;

import lombok.Builder;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@Builder
public class WorkoutRequest {
    private String workoutDate;
    private List<String> workoutTypes;
    private Integer duration;
    private List<MultipartFile> images;
}