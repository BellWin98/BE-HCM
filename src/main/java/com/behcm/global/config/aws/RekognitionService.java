package com.behcm.global.config.aws;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;

import java.util.List;

@Service
public class RekognitionService {

    private final RekognitionClient rekognitionClient;

    @Autowired
    public RekognitionService(RekognitionClient rekognitionClient) {
        this.rekognitionClient = rekognitionClient;
    }

    public boolean isExercisePhoto(String bucketName, String objectKey) {
        Image image = Image.builder()
                .s3Object(S3Object.builder()
                        .bucket(bucketName)
                        .name(objectKey)
                        .build())
                .build();

        DetectLabelsRequest request = DetectLabelsRequest.builder()
                .image(image)
                .maxLabels(10)
                .minConfidence(80F)
                .build();

        DetectTextRequest textRequest = DetectTextRequest.builder()
                .image(image)
                .build();

        DetectLabelsResponse response = rekognitionClient.detectLabels(request);
        DetectTextResponse textResponse = rekognitionClient.detectText(textRequest);
        List<TextDetection> textDetections = textResponse.textDetections();

        System.out.println("=== 텍스트 감지 결과 ===");
        for (TextDetection text : textDetections) {
            System.out.printf("텍스트: %s (신뢰도: %.2f%%, 타입: %s)\n",
                    text.detectedText(),
                    text.confidence(),
                    text.type());
        }

        List<Label> labels = response.labels();
        System.out.println("=== 분석 결과 ===");
        for (Label label : labels) {
            System.out.printf("%s (%.2f%%)\n", label.name(), label.confidence());
        }

        return labels.stream().anyMatch(label -> label.name().equalsIgnoreCase("Sports") ||
                label.name().equalsIgnoreCase("Fitness") ||
                label.name().equalsIgnoreCase("Exercise") ||
                label.name().equalsIgnoreCase("Person"));
    }

}
