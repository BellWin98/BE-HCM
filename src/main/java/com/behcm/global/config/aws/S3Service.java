package com.behcm.global.config.aws;

import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

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
    /**
     * 업로드 키가 UUID 기반이라 같은 URL의 내용이 바뀌지 않으므로 장기 캐싱이 안전하다.
     * 이 헤더가 없으면 브라우저가 휴리스틱 캐싱(Last-Modified 경과 시간의 10%)을 적용해
     * 방금 올린 이미지는 사실상 캐시되지 않고 조회할 때마다 다시 내려받는다.
     */
    private static final String IMAGE_CACHE_CONTROL = "public, max-age=31536000, immutable";
    private static final String PROFILE_IMAGE_PREFIX = "profiles/";
    private static final String CHAT_IMAGE_PREFIX = "chat/rooms/";
    private static final String WORKOUT_IMAGE_PREFIX = "workout/";
    private static final DateTimeFormatter FOLDER_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp");

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    private final S3Client s3Client;

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
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentLength(file.getSize())
                    .contentType(file.getContentType())
                    .cacheControl(IMAGE_CACHE_CONTROL)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, file.getSize()));
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
        GetUrlRequest getUrlRequest = GetUrlRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        return s3Client.utilities().getUrl(getUrlRequest).toString();
    }

    private String getFolderName() {
        return LocalDate.now().format(FOLDER_DATE_FORMATTER);
    }

    private String generateFileName(String originalFilename) {
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        return UUID.randomUUID() + extension;
    }
}