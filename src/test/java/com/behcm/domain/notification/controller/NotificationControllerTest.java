package com.behcm.domain.notification.controller;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.notification.dto.FcmTokenRequest;
import com.behcm.domain.notification.dto.NotifyRequest;
import com.behcm.domain.notification.service.NotificationFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NotificationFacade notificationFacade;

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

    @Test
    @DisplayName("notifyAllRoomMembers는 인증된 사용자의 모든 방 멤버에게 알림 발송을 위임한다")
    void notifyAllRoomMembers_delegatesToFacade() throws Exception {
        NotifyRequest request = new NotifyRequest();
        request.setTitle("공지");
        request.setBody("오늘도 화이팅!");
        request.setType("ANNOUNCE");

        mockMvc.perform(post("/api/notifications/rooms")
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(notificationFacade).notifyAllRoomMembers(
                any(Member.class), eq("공지"), eq("오늘도 화이팅!"), eq("ANNOUNCE"), eq(""));
    }

    @Test
    @DisplayName("notifyAllRoomMembers는 본문이 50자를 초과하면 잘라서 전달한다")
    void notifyAllRoomMembers_longBody_truncatesTo50Chars() throws Exception {
        String longBody = "가".repeat(60);
        NotifyRequest request = new NotifyRequest();
        request.setTitle("공지");
        request.setBody(longBody);
        request.setType("ANNOUNCE");

        mockMvc.perform(post("/api/notifications/rooms")
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        String expectedTruncated = "가".repeat(50) + "...";
        verify(notificationFacade).notifyAllRoomMembers(
                any(Member.class), eq("공지"), eq(expectedTruncated), eq("ANNOUNCE"), eq(""));
    }

    @Test
    @DisplayName("notifyRoomMembersForAdmin은 지정된 방 멤버에게 알림 발송을 위임한다")
    void notifyRoomMembersForAdmin_delegatesToFacadeWithRoomId() throws Exception {
        NotifyRequest request = new NotifyRequest();
        request.setTitle("공지");
        request.setBody("공지 내용");
        request.setType("ANNOUNCE");

        mockMvc.perform(post("/api/notifications/rooms/{roomId}", 5L)
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(notificationFacade).notifyRoomMembers(
                eq(5L), any(Member.class), eq("공지"), eq("공지 내용"), eq("ANNOUNCE"), eq(""));
    }
}
