package com.behcm.global.config.aws;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RekognitionController {

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    private final RekognitionService rekognitionService;

    @Autowired
    public RekognitionController(final RekognitionService rekognitionService) {
        this.rekognitionService = rekognitionService;
    }

    @GetMapping("/api/auth/analyze-photo")
    public String analyzePhoto(@RequestParam String key) {
        boolean isExercise = rekognitionService.isExercisePhoto(bucket, key);

        return isExercise ? "운동 인증 성공" : "운동 인증 실패";
    }
}
