package com.behcm.global.config.aws;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.net.URL;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    private static final String BUCKET = "test-bucket";

    @Mock
    private AmazonS3Client amazonS3Client;

    @InjectMocks
    private S3Service s3Service;

    @Captor
    private ArgumentCaptor<PutObjectRequest> putObjectRequestCaptor;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(s3Service, "bucket", BUCKET);
        given(amazonS3Client.getUrl(org.mockito.ArgumentMatchers.eq(BUCKET), org.mockito.ArgumentMatchers.anyString()))
                .willReturn(new URL("https://test-bucket.s3.amazonaws.com/key.jpg"));
    }

    private MultipartFile imageFile() {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());
    }

    @Test
    @DisplayName("운동 인증 이미지 업로드 시 장기 캐싱용 Cache-Control 헤더가 설정된다")
    void uploadWorkoutImages_setsCacheControl() {
        s3Service.uploadWorkoutImages(List.of(imageFile()));

        verify(amazonS3Client).putObject(putObjectRequestCaptor.capture());
        assertThat(putObjectRequestCaptor.getValue().getMetadata().getCacheControl())
                .isEqualTo("public, max-age=31536000, immutable");
    }

    @Test
    @DisplayName("프로필/채팅 이미지 업로드에도 동일한 Cache-Control 헤더가 적용된다")
    void uploadProfileAndChatImage_setCacheControl() {
        s3Service.uploadProfileImage(imageFile());
        s3Service.uploadChatImage(imageFile(), 1L);

        verify(amazonS3Client, org.mockito.Mockito.times(2)).putObject(putObjectRequestCaptor.capture());
        assertThat(putObjectRequestCaptor.getAllValues())
                .allSatisfy(request -> assertThat(request.getMetadata().getCacheControl())
                        .isEqualTo("public, max-age=31536000, immutable"));
    }

    @Test
    @DisplayName("Content-Type 과 Content-Length 는 기존대로 유지된다")
    void uploadImage_keepsContentMetadata() {
        MultipartFile file = imageFile();

        s3Service.uploadWorkoutImages(List.of(file));

        verify(amazonS3Client).putObject(putObjectRequestCaptor.capture());
        assertThat(putObjectRequestCaptor.getValue().getMetadata().getContentType()).isEqualTo("image/jpeg");
        assertThat(putObjectRequestCaptor.getValue().getMetadata().getContentLength()).isEqualTo(file.getSize());
    }
}
