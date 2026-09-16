package com.behcm.domain.notification.controller;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.notification.dto.FcmTokenRequest;
import com.behcm.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerTest extends IntegrationTestSupport {

    private Member member() {
        return Member.builder()
                .email("user@test.com")
                .password("encoded")
                .nickname("user")
                .role(MemberRole.USER)
                .build();
    }

    @Test
    @DisplayName("registerFcmToken은 토큰이 비어있으면 400을 반환한다")
    void registerFcmToken_blankToken_returnsBadRequest() throws Exception {
        FcmTokenRequest request = new FcmTokenRequest();
        request.setToken("");

        mockMvc.perform(post("/api/notifications/fcm/token")
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(notificationFacade, never()).registerFcmToken(any(), any());
    }

    @Test
    @DisplayName("registerFcmToken은 유효한 토큰이면 등록을 위임하고 200을 반환한다")
    void registerFcmToken_validToken_delegatesToFacade() throws Exception {
        FcmTokenRequest request = new FcmTokenRequest();
        request.setToken("fcm-token-value");

        mockMvc.perform(post("/api/notifications/fcm/token")
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        verify(notificationFacade).registerFcmToken(any(Member.class), eq("fcm-token-value"));
    }
}
