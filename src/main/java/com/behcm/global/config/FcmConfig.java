package com.behcm.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Configuration
public class FcmConfig {

    @Value("${fcm.service-account-file:firebase-service-account.json}")
    private String serviceAccountFile;

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                InputStream serviceAccount;

                try {
                    // 먼저 resources 디렉토리에서 찾기
                    serviceAccount = new ClassPathResource(serviceAccountFile).getInputStream();
                    log.info("Firebase service account file loaded from classpath: {}", serviceAccountFile);
                } catch (Exception e) {
                    // resources에 없으면 절대 경로로 찾기
                    serviceAccount = new FileInputStream(serviceAccountFile);
                    log.info("Firebase service account file loaded from file system: {}", serviceAccountFile);
                }

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("FirebaseApp initialized successfully");
            }
        } catch (IOException e) {
            log.error("Failed to initialize FirebaseApp: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Firebase", e);
        }
    }
}