package com.behcm.domain.auth.service;

import com.behcm.domain.auth.entity.EmailVerification;
import com.behcm.domain.auth.repository.EmailVerificationRepository;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    private EmailVerification verification(String email, String code, LocalDateTime expiresAt) {
        EmailVerification verification = EmailVerification.builder()
                .email(email)
                .verificationCode(code)
                .expiresAt(expiresAt)
                .build();
        setId(verification, 1L);
        return verification;
    }

    private void setId(Object entity, long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("checkEmailDuplicate는 이미 가입된 이메일이면 EMAIL_ALREADY_EXISTS 예외를 던진다")
    void checkEmailDuplicate_existingEmail_throwsEmailAlreadyExists() {
        given(memberRepository.existsByEmail("dup@test.com")).willReturn(true);

        assertThatThrownBy(() -> emailVerificationService.checkEmailDuplicate("dup@test.com"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("checkEmailDuplicate는 신규 이메일이면 예외 없이 통과한다")
    void checkEmailDuplicate_newEmail_doesNotThrow() {
        given(memberRepository.existsByEmail("new@test.com")).willReturn(false);

        emailVerificationService.checkEmailDuplicate("new@test.com");
    }

    @Test
    @DisplayName("verifyEmailCode는 인증코드가 없으면 VERIFICATION_CODE_NOT_FOUND 예외를 던진다")
    void verifyEmailCode_noCode_throwsVerificationCodeNotFound() {
        given(emailVerificationRepository.findFirstByEmailAndIsVerifiedFalseOrderByCreatedAtDesc("user@test.com"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.verifyEmailCode("user@test.com", "123456"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VERIFICATION_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("verifyEmailCode는 만료된 코드면 VERIFICATION_CODE_EXPIRED 예외를 던진다")
    void verifyEmailCode_expiredCode_throwsVerificationCodeExpired() {
        EmailVerification expired = verification("user@test.com", "123456", LocalDateTime.now().minusMinutes(1));
        given(emailVerificationRepository.findFirstByEmailAndIsVerifiedFalseOrderByCreatedAtDesc("user@test.com"))
                .willReturn(Optional.of(expired));

        assertThatThrownBy(() -> emailVerificationService.verifyEmailCode("user@test.com", "123456"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VERIFICATION_CODE_EXPIRED);
    }

    @Test
    @DisplayName("verifyEmailCode는 코드가 일치하지 않으면 INVALID_VERIFICATION_CODE 예외를 던진다")
    void verifyEmailCode_wrongCode_throwsInvalidVerificationCode() {
        EmailVerification valid = verification("user@test.com", "123456", LocalDateTime.now().plusMinutes(5));
        given(emailVerificationRepository.findFirstByEmailAndIsVerifiedFalseOrderByCreatedAtDesc("user@test.com"))
                .willReturn(Optional.of(valid));

        assertThatThrownBy(() -> emailVerificationService.verifyEmailCode("user@test.com", "000000"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_VERIFICATION_CODE);
    }

    @Test
    @DisplayName("verifyEmailCode는 유효한 코드가 일치하면 인증완료 처리한다")
    void verifyEmailCode_correctCode_marksVerified() {
        EmailVerification valid = verification("user@test.com", "123456", LocalDateTime.now().plusMinutes(5));
        given(emailVerificationRepository.findFirstByEmailAndIsVerifiedFalseOrderByCreatedAtDesc("user@test.com"))
                .willReturn(Optional.of(valid));

        emailVerificationService.verifyEmailCode("user@test.com", "123456");

        assertThat(valid.getIsVerified()).isTrue();
    }

    @Test
    @DisplayName("sendEmail이 예외를 던지면 EMAIL_SEND_FAILED로 감싸서 던진다")
    void sendEmail_mailSenderThrows_wrapsAsEmailSendFailed() {
        org.mockito.Mockito.doThrow(new RuntimeException("smtp down"))
                .when(mailSender).send(org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));

        assertThatThrownBy(() -> emailVerificationService.sendEmail("user@test.com", "123456"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_SEND_FAILED);

        verify(mailSender).send(org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    @DisplayName("sendVerificationEmail은 기존 미인증 코드를 삭제한 뒤 새 코드를 저장하고 메일을 발송한다")
    void sendVerificationEmail_savesNewCodeAndSendsMail() {
        given(memberRepository.existsByEmail("user@test.com")).willReturn(false);

        emailVerificationService.sendVerificationEmail("user@test.com");

        verify(emailVerificationRepository).deleteByEmailAndIsVerifiedFalse("user@test.com");
        verify(emailVerificationRepository).save(org.mockito.ArgumentMatchers.any(EmailVerification.class));
        verify(mailSender).send(org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    @DisplayName("sendVerificationEmail은 이미 가입된 이메일이면 코드 저장/발송 없이 예외를 던진다")
    void sendVerificationEmail_duplicateEmail_doesNotSaveOrSend() {
        given(memberRepository.existsByEmail("dup@test.com")).willReturn(true);

        assertThatThrownBy(() -> emailVerificationService.sendVerificationEmail("dup@test.com"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_ALREADY_EXISTS);

        verify(emailVerificationRepository, never()).deleteByEmailAndIsVerifiedFalse(org.mockito.ArgumentMatchers.any());
        verify(emailVerificationRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));
    }
}
