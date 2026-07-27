package com.behcm.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Configuration
@Profile({"dev", "prod"})
public class FcmConfig {

    @Value("${fcm.key-path}")
    private String path;

    @PostConstruct
    public void init() {
/*        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(new ClassPathResource(path).getInputStream()))
                .build();*/

        try {
            // ClassPathResource 대신 환경에 따라 외부 파일을 읽을 수 있도록 구성
            InputStream serviceAccount = new FileInputStream(path);

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                log.info("FCM FirebaseApp initialized successfully.");
            }
        } catch (IOException e) {
            // 키 파일이 없으면 FirebaseApp이 초기화되지 않아 이후 모든 발송이 실패하므로,
            // 조용히 넘기지 말고 명확히 로깅한다.
            log.error("FCM FirebaseApp 초기화 실패 - key path: {}. 이후 푸시 발송이 동작하지 않습니다.", path, e);
        }
    }
}