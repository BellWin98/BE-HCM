package com.behcm.domain.rest.controller;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.rest.dto.RestRequest;
import com.behcm.domain.rest.service.RestService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RestService restService;

    private Member member() {
        return Member.builder()
                .email("user@test.com")
                .password("encoded")
                .nickname("user")
                .role(MemberRole.USER)
                .build();
    }

    private RestRequest request() {
        RestRequest request = new RestRequest();
        request.setReason("여행");
        request.setStartDate("2026-08-01");
        request.setEndDate("2026-08-05");
        return request;
    }

    @Test
    @DisplayName("registerRestDay는 인증 없이 요청하면 401을 반환한다")
    void registerRestDay_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/rest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("registerRestDay는 사유가 비어있으면 400을 반환한다")
    void registerRestDay_blankReason_returnsBadRequest() throws Exception {
        RestRequest request = request();
        request.setReason("");

        mockMvc.perform(post("/api/rest")
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(restService, never()).registerRestDay(any(), any());
    }

    @Test
    @DisplayName("registerRestDay는 유효한 요청이면 서비스에 위임하고 200을 반환한다")
    void registerRestDay_validRequest_delegatesToService() throws Exception {
        mockMvc.perform(post("/api/rest")
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        verify(restService).registerRestDay(any(Member.class), any(RestRequest.class));
    }

    @Test
    @DisplayName("registerRestDay는 휴식 기간이 겹치면 서비스 예외의 상태코드를 그대로 응답한다")
    void registerRestDay_overlappingPeriod_returnsServiceErrorStatus() throws Exception {
        org.mockito.Mockito.doThrow(new CustomException(ErrorCode.REST_PERIOD_OVERLAP))
                .when(restService).registerRestDay(any(Member.class), any(RestRequest.class));

        mockMvc.perform(post("/api/rest")
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is(ErrorCode.REST_PERIOD_OVERLAP.getMessage())));
    }
}
