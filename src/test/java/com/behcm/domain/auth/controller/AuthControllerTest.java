package com.behcm.domain.auth.controller;

import com.behcm.domain.auth.dto.AuthResponse;
import com.behcm.domain.auth.dto.EmailRequest;
import com.behcm.domain.auth.dto.EmailVerificationConfirmRequest;
import com.behcm.domain.auth.dto.LoginRequest;
import com.behcm.domain.auth.dto.RefreshTokenRequest;
import com.behcm.domain.auth.dto.RegisterRequest;
import com.behcm.domain.auth.service.AuthService;
import com.behcm.domain.auth.service.EmailVerificationService;
import com.behcm.domain.member.dto.MemberResponse;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    private AuthResponse authResponse() {
        MemberResponse member = MemberResponse.builder()
                .id(1L)
                .email("user@test.com")
                .nickname("user")
                .role(MemberRole.USER)
                .build();
        return new AuthResponse("access-token", "refresh-token", member);
    }

    @Test
    @DisplayName("register는 유효한 요청이면 200과 토큰을 반환한다")
    void register_validRequest_returnsOk() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@test.com");
        request.setPassword("Password1!");
        request.setNickname("user");

        given(authService.register(any(RegisterRequest.class))).willReturn(authResponse());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.accessToken", is("access-token")))
                .andExpect(jsonPath("$.data.member.email", is("user@test.com")));
    }

    @Test
    @DisplayName("register는 이메일 형식이 올바르지 않으면 400을 반환하고 서비스는 호출되지 않는다")
    void register_invalidEmail_returnsBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("not-an-email");
        request.setPassword("Password1!");
        request.setNickname("user");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));

        verify(authService, org.mockito.Mockito.never()).register(any());
    }

    @Test
    @DisplayName("register는 이미 사용 중인 이메일이면 서비스 예외 상태코드를 그대로 응답한다")
    void register_duplicateEmail_returnsServiceErrorStatus() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("dup@test.com");
        request.setPassword("Password1!");
        request.setNickname("user");

        given(authService.register(any(RegisterRequest.class)))
                .willThrow(new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is(ErrorCode.EMAIL_ALREADY_EXISTS.getMessage())));
    }

    @Test
    @DisplayName("login은 유효한 요청이면 200과 토큰을 반환한다")
    void login_validRequest_returnsOk() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@test.com");
        request.setPassword("Password1!");

        given(authService.login(any(LoginRequest.class))).willReturn(authResponse());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", is("access-token")));
    }

    @Test
    @DisplayName("refresh는 유효한 요청이면 200과 새 토큰을 반환한다")
    void refreshToken_validRequest_returnsOk() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("old-refresh-token");

        given(authService.refreshToken("old-refresh-token")).willReturn(authResponse());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshToken", is("refresh-token")));
    }

    @Test
    @DisplayName("refresh는 refreshToken이 비어있으면 400을 반환한다")
    void refreshToken_blank_returnsBadRequest() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("check-email은 중복 확인을 위임하고 200을 반환한다")
    void checkEmailDuplicate_validRequest_returnsOk() throws Exception {
        EmailRequest request = new EmailRequest();
        request.setEmail("user@test.com");

        mockMvc.perform(post("/api/auth/check-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        verify(emailVerificationService).checkEmailDuplicate("user@test.com");
    }

    @Test
    @DisplayName("send-verification은 인증 메일 발송을 위임하고 200을 반환한다")
    void sendVerificationEmail_validRequest_returnsOk() throws Exception {
        EmailRequest request = new EmailRequest();
        request.setEmail("user@test.com");

        mockMvc.perform(post("/api/auth/send-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(emailVerificationService).sendVerificationEmail("user@test.com");
    }

    @Test
    @DisplayName("verify-email은 인증코드가 6자리 숫자가 아니면 400을 반환한다")
    void verifyEmailCode_invalidCodeFormat_returnsBadRequest() throws Exception {
        EmailVerificationConfirmRequest request = new EmailVerificationConfirmRequest();
        request.setEmail("user@test.com");
        request.setCode("abc");

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(emailVerificationService, org.mockito.Mockito.never()).verifyEmailCode(anyString(), anyString());
    }

    @Test
    @DisplayName("verify-email은 유효한 코드면 검증을 위임하고 200을 반환한다")
    void verifyEmailCode_validRequest_returnsOk() throws Exception {
        EmailVerificationConfirmRequest request = new EmailVerificationConfirmRequest();
        request.setEmail("user@test.com");
        request.setCode("123456");

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(emailVerificationService).verifyEmailCode(eq("user@test.com"), eq("123456"));
    }
}
