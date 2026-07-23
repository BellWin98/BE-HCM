package com.behcm.global.config.aws;

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
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    private static final String BUCKET = "test-bucket";

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Utilities s3Utilities;

    @InjectMocks
    private S3Service s3Service;

    @Captor
    private ArgumentCaptor<PutObjectRequest> putObjectRequestCaptor;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(s3Service, "bucket", BUCKET);
        given(s3Client.utilities()).willReturn(s3Utilities);
        given(s3Utilities.getUrl(any(GetUrlRequest.class)))
                .willReturn(URI.create("https://test-bucket.s3.amazonaws.com/key.jpg").toURL());
    }

    private MultipartFile imageFile() {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());
    }

    @Test
    @DisplayName("운동 인증 이미지 업로드 시 장기 캐싱용 Cache-Control 헤더가 설정된다")
    void uploadWorkoutImages_setsCacheControl() {
        s3Service.uploadWorkoutImages(List.of(imageFile()));

        verify(s3Client).putObject(putObjectRequestCaptor.capture(), any(RequestBody.class));
        assertThat(putObjectRequestCaptor.getValue().cacheControl())
                .isEqualTo("public, max-age=31536000, immutable");
    }

    @Test
    @DisplayName("프로필/채팅 이미지 업로드에도 동일한 Cache-Control 헤더가 적용된다")
    void uploadProfileAndChatImage_setCacheControl() {
        s3Service.uploadProfileImage(imageFile());
        s3Service.uploadChatImage(imageFile(), 1L);

        verify(s3Client, org.mockito.Mockito.times(2))
                .putObject(putObjectRequestCaptor.capture(), any(RequestBody.class));
        assertThat(putObjectRequestCaptor.getAllValues())
                .allSatisfy(request -> assertThat(request.cacheControl())
                        .isEqualTo("public, max-age=31536000, immutable"));
    }

    @Test
    @DisplayName("Content-Type 과 Content-Length 는 기존대로 유지된다")
    void uploadImage_keepsContentMetadata() {
        MultipartFile file = imageFile();

        s3Service.uploadWorkoutImages(List.of(file));

        verify(s3Client).putObject(putObjectRequestCaptor.capture(), any(RequestBody.class));
        assertThat(putObjectRequestCaptor.getValue().contentType()).isEqualTo("image/jpeg");
        assertThat(putObjectRequestCaptor.getValue().contentLength()).isEqualTo(file.getSize());
    }
}
