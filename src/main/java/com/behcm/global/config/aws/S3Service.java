package com.behcm.global.config.aws;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private static final long IMAGE_MAX_SIZE_BYTES = 20 * 1024 * 1024; // 20MB
    private static final String PROFILE_IMAGE_PREFIX = "profiles/";
    private static final String CHAT_IMAGE_PREFIX = "chat/rooms/";
    private static final String WORKOUT_IMAGE_PREFIX = "workout/";
    private static final DateTimeFormatter FOLDER_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp");

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    private final AmazonS3Client amazonS3Client;

    public String uploadProfileImage(MultipartFile file) {
        return uploadImage(file, PROFILE_IMAGE_PREFIX);
    }

    /**
     * S3 키: chat/rooms/{roomId}/{yyyy/MM/dd}/{uuid}.{ext}
     */
    public String uploadChatImage(MultipartFile file, Long roomId) {
        String prefix = CHAT_IMAGE_PREFIX + roomId + "/";
        return uploadImage(file, prefix);
    }

    public List<String> uploadWorkoutImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_FILE);
        }

        List<String> uploadedUrls = new ArrayList<>();
        for (MultipartFile file : files) {
            String uploadedUrl = uploadWorkoutImage(file);
            uploadedUrls.add(uploadedUrl);
        }
        return uploadedUrls;
    }

    private String uploadWorkoutImage(MultipartFile file) {
        return uploadImage(file, WORKOUT_IMAGE_PREFIX);
    }

    private String uploadImage(MultipartFile file, String keyPrefix) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_FILE);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
        }

        if (file.getSize() > IMAGE_MAX_SIZE_BYTES) {
            throw new CustomException(ErrorCode.FILE_TOO_LARGE);
        }

        if (!isAllowedContentType(file.getContentType())) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
        }

        if (!isValidImageFile(originalFilename)) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
        }

        String fileName = generateFileName(originalFilename);
        String filePath = keyPrefix + getFolderName();
        String key = filePath + "/" + fileName;

        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentLength(file.getSize());
            objectMetadata.setContentType(file.getContentType());

            amazonS3Client.putObject(new PutObjectRequest(bucket, key, inputStream, objectMetadata));
            return getFileUrl(key);
        } catch (IOException e) {
            log.error("S3 파일 업로드 실패: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    private boolean isAllowedContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lowerCaseContentType = contentType.toLowerCase(Locale.ROOT);
        return ALLOWED_CONTENT_TYPES.contains(lowerCaseContentType);
    }

    private boolean isValidImageFile(String filename) {
        if (filename == null) {
            return false;
        }
        String lowerCaseFilename = filename.toLowerCase(Locale.ROOT);
        for (String ext : ALLOWED_IMAGE_EXTENSIONS) {
            if (lowerCaseFilename.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private String getFileUrl(String key) {
        return amazonS3Client.getUrl(bucket, key).toString();
    }

    private String getFolderName() {
        return LocalDate.now().format(FOLDER_DATE_FORMATTER);
    }

    private String generateFileName(String originalFilename) {
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        return UUID.randomUUID() + extension;
    }
}