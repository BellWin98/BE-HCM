package com.behcm.domain.auth.service;

import com.behcm.domain.auth.entity.EmailVerification;
import com.behcm.domain.auth.repository.EmailVerificationRepository;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import com.behcm.global.logging.LogMask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class EmailVerificationService {

    private final EmailVerificationRepository emailVerificationRepository;
    private final MemberRepository memberRepository;
    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    public void checkEmailDuplicate(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    @Async("mailExecutor")
    public void sendVerificationEmail(String email) {
        checkEmailDuplicate(email);

        // 기존 미인증코드 삭제
        emailVerificationRepository.deleteByEmailAndIsVerifiedFalse(email);

        // 인증코드 생성 (6자리)
        String verificationCode = String.format("%06d", secureRandom.nextInt(1000000));

        // 만료 시간 설정 (5분)
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        // 이메일 인증 정보 저장
        EmailVerification emailVerification = EmailVerification.builder()
                .email(email)
                .verificationCode(verificationCode)
                .expiresAt(expiresAt)
                .build();

        emailVerificationRepository.save(emailVerification);

        // 이메일 발송
        sendEmail(email, verificationCode);
        log.info("Verification email sent (email={})", LogMask.email(email));
    }

    public void verifyEmailCode(String email, String code) {
        EmailVerification emailVerification = emailVerificationRepository.findFirstByEmailAndIsVerifiedFalseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new CustomException(ErrorCode.VERIFICATION_CODE_NOT_FOUND));

        // 만료 시간 확인
        if (emailVerification.isExpired()) {
            throw new CustomException(ErrorCode.VERIFICATION_CODE_EXPIRED);
        }

        // 인증코드 확인
        if (!emailVerification.getVerificationCode().equals(code)) {
            // 6자리 코드는 5분 안에 무차별 대입이 가능하다. 같은 이메일로 반복되면 봐야 하므로 대상을 남긴다.
            log.warn("Verification code mismatch (email={})", LogMask.email(email));
            throw new CustomException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        // 인증완료 처리
        emailVerification.verify();

        log.info("Email verified (email={})", LogMask.email(email));
    }

    public void sendEmail(String to, String verificationCode) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("HCM 이메일 인증 코드");
            message.setText(String.format("""
                    안녕하세요. 헬창마을(HCM) 입니다.
                    회원가입을 위한 이메일 인증 코드입니다.
                    
                    인증 코드: %s
                    
                    이 코드는 5분 후에 만료됩니다. 감사합니다.
                    """, verificationCode));
            mailSender.send(message);
        } catch (Exception e) {
            // 메일 서버(SMTP 자격증명·네트워크) 문제다. 가입 자체가 막히므로 개입이 필요하다.
            log.error("Failed to send verification email (email={})", LogMask.email(to), e);
            throw new CustomException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }
}
