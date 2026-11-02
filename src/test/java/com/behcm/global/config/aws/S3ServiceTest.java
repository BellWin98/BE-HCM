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

import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    private static final String BUCKET = "test-bucket";
    private static final String BASE_URL = "https://test-bucket.s3.amazonaws.com/";

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
        lenient().when(s3Client.utilities()).thenReturn(s3Utilities);
        lenient().when(s3Utilities.getUrl(any(GetUrlRequest.class))).thenAnswer(invocation ->
                URI.create(BASE_URL + invocation.<GetUrlRequest>getArgument(0).key()).toURL());
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

    private MultipartFile file(String name, String contentType) {
        return new MockMultipartFile("file", name, contentType, "fake-image-bytes".getBytes());
    }

    @Test
    @DisplayName("지식 메모 이미지는 knowledge-notes/ 날짜 폴더 아래 UUID 키로 올라간다")
    void uploadKnowledgeNoteImages_usesKnowledgeNotesPrefix() {
        List<String> urls = s3Service.uploadKnowledgeNoteImages(List.of(file("a.png", "image/png")));

        verify(s3Client).putObject(putObjectRequestCaptor.capture(), any(RequestBody.class));
        assertThat(putObjectRequestCaptor.getValue().key())
                .matches("^knowledge-notes/\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}\\.png$");
        assertThat(putObjectRequestCaptor.getValue().contentType()).isEqualTo("image/png");
        assertThat(urls).hasSize(1);
    }

    @Test
    @DisplayName("지식 메모 이미지는 GIF 를 허용한다")
    void uploadKnowledgeNoteImages_allowsGif() {
        s3Service.uploadKnowledgeNoteImages(List.of(file("a.gif", "image/gif")));

        verify(s3Client).putObject(putObjectRequestCaptor.capture(), any(RequestBody.class));
        assertThat(putObjectRequestCaptor.getValue().contentType()).isEqualTo("image/gif");
    }

    @Test
    @DisplayName("지식 메모 이미지는 SVG 를 거부한다")
    void uploadKnowledgeNoteImages_rejectsSvg() {
        assertThatThrownBy(() -> s3Service.uploadKnowledgeNoteImages(List.of(file("a.svg", "image/svg+xml"))))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_FILE_TYPE));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("지식 메모 이미지는 HEIC 를 거부한다")
    void uploadKnowledgeNoteImages_rejectsHeic() {
        assertThatThrownBy(() -> s3Service.uploadKnowledgeNoteImages(List.of(file("a.heic", "image/heic"))))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_FILE_TYPE));
    }

    @Test
    @DisplayName("지식 메모 이미지는 확장자 없는 파일명이어도 Content-Type 으로 판단해 올린다")
    void uploadKnowledgeNoteImages_acceptsFilenameWithoutExtension() {
        s3Service.uploadKnowledgeNoteImages(List.of(file("shared-0", "image/png")));

        verify(s3Client).putObject(putObjectRequestCaptor.capture(), any(RequestBody.class));
        assertThat(putObjectRequestCaptor.getValue().key())
                .matches("^knowledge-notes/\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}\\.png$");
    }

    @Test
    @DisplayName("지식 메모 키 확장자는 파일명이 아닌 Content-Type 에서 정한다")
    void uploadKnowledgeNoteImages_extensionFromContentType() {
        s3Service.uploadKnowledgeNoteImages(List.of(file("photo.jpeg", "image/webp")));

        verify(s3Client).putObject(putObjectRequestCaptor.capture(), any(RequestBody.class));
        assertThat(putObjectRequestCaptor.getValue().key()).endsWith(".webp");
    }

    @Test
    @DisplayName("지식 메모 이미지는 하나라도 거부되면 어떤 파일도 올리지 않는다")
    void uploadKnowledgeNoteImages_validatesAllBeforeUploading() {
        assertThatThrownBy(() -> s3Service.uploadKnowledgeNoteImages(List.of(
                file("a.png", "image/png"), file("b.png", "image/png"), file("c.svg", "image/svg+xml"))))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_FILE_TYPE));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("프로필 업로드는 여전히 확장자 없는 파일명을 거부한다")
    void profileUpload_stillRequiresExtension() {
        assertThatThrownBy(() -> s3Service.uploadProfileImage(file("shared-0", "image/png")))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_FILE_TYPE));
    }

    @Test
    @DisplayName("프로필 업로드는 여전히 GIF 를 거부한다")
    void profileUpload_stillRejectsGif() {
        assertThatThrownBy(() -> s3Service.uploadProfileImage(file("a.gif", "image/gif")))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_FILE_TYPE));
    }

    @Test
    @DisplayName("isKnowledgeNoteImageUrl 은 이 버킷의 knowledge-notes/ URL 만 true")
    void isKnowledgeNoteImageUrl() {
        assertThat(s3Service.isKnowledgeNoteImageUrl(BASE_URL + "knowledge-notes/2026/11/03/x.png")).isTrue();
        assertThat(s3Service.isKnowledgeNoteImageUrl(BASE_URL + "workout/2026/11/03/x.png")).isFalse();
        assertThat(s3Service.isKnowledgeNoteImageUrl("https://evil.example.com/knowledge-notes/2026/11/03/x.png")).isFalse();
        assertThat(s3Service.isKnowledgeNoteImageUrl(null)).isFalse();
    }

    @Test
    @DisplayName("deleteByUrl 은 URL 에서 키를 뽑아 deleteObject 를 호출한다")
    void deleteByUrl_deletesDerivedKey() {
        s3Service.deleteByUrl(BASE_URL + "knowledge-notes/2026/11/03/x.png");

        org.mockito.ArgumentCaptor<DeleteObjectRequest> captor = org.mockito.ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo("knowledge-notes/2026/11/03/x.png");
    }

    @Test
    @DisplayName("deleteByUrl 은 이 버킷 URL 이 아니면 아무것도 하지 않는다")
    void deleteByUrl_ignoresForeignUrl() {
        s3Service.deleteByUrl("https://evil.example.com/knowledge-notes/x.png");
        s3Service.deleteByUrl(null);

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }
}
