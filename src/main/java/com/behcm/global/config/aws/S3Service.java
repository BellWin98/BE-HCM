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
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    private final AmazonS3Client amazonS3Client;

    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_FILE);
        }

        // 파일 확장자 검증
        String originalFilename = file.getOriginalFilename();
        if (!isValidImageFile(originalFilename)) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
        }

        String uploadFileUrl;
        String fileName = generateFileName(originalFilename);
        String filePath = getFolderName();
        String key = filePath + "/" +fileName;

        try (InputStream inputStream = file.getInputStream()){
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentLength(file.getSize());
            objectMetadata.setContentType(file.getContentType());

            amazonS3Client.putObject(new PutObjectRequest(bucket, key, inputStream, objectMetadata));
            uploadFileUrl = getFileUrl(key);

            return uploadFileUrl;
        } catch (IOException e) {
            log.error("S3 파일 업로드 실패: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    public List<String> uploadImages(java.util.List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_FILE);
        }

        List<String> uploadedUrls = new ArrayList<>();
        for (MultipartFile file : files) {
            String uploadedUrl = uploadImage(file);
            uploadedUrls.add(uploadedUrl);
        }
        return uploadedUrls;
    }

    private boolean isValidImageFile(String filename) {
        String[] validExtensions = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"};
        String lowerCaseFilename = filename.toLowerCase();
        for (String ext : validExtensions) {
            if (lowerCaseFilename.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    public String getFileUrl(String key) {
        return amazonS3Client.getUrl(bucket, key).toString();
    }

    private String getFolderName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Date date = new Date();
        String str = sdf.format(date);

        return str.replace("-", "/");
    }

    private String generateFileName(String originalFilename) {
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        return UUID.randomUUID() + extension;
    }
}