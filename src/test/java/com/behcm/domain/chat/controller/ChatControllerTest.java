package com.behcm.domain.chat.controller;

import com.behcm.domain.chat.dto.ChatHistoryResponse;
import com.behcm.domain.chat.dto.ChatImageUploadResponse;
import com.behcm.domain.chat.service.ChatService;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    private Member member() {
        return Member.builder()
                .email("user@test.com")
                .password("encoded")
                .nickname("user")
                .role(MemberRole.USER)
                .build();
    }

    @Test
    @DisplayName("uploadChatImage는 인증 없이 요청하면 401을 반환한다")
    void uploadChatImage_withoutAuthentication_returnsUnauthorized() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});

        mockMvc.perform(multipart("/api/chat/rooms/{roomId}/images", 1L).file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("uploadChatImage는 업로드된 이미지 URL을 반환한다")
    void uploadChatImage_validRequest_returnsUploadedUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});
        given(chatService.uploadChatImage(any(Member.class), eq(1L), any()))
                .willReturn(ChatImageUploadResponse.of("https://s3/chat/1/a.png"));

        mockMvc.perform(multipart("/api/chat/rooms/{roomId}/images", 1L).file(file).with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl", is("https://s3/chat/1/a.png")));
    }

    @Test
    @DisplayName("getChatHistory는 커서 없이 요청하면 초기 로드로 서비스에 위임한다")
    void getChatHistory_withoutCursor_delegatesInitialLoad() throws Exception {
        ChatHistoryResponse response = new ChatHistoryResponse(Collections.emptyList(), null, false);
        given(chatService.getChatHistory(any(Member.class), eq(1L), isNull(), eq(20))).willReturn(response);

        mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", 1L).with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext", is(false)));
    }

    @Test
    @DisplayName("getChatHistory는 커서와 size 파라미터를 그대로 서비스에 전달한다")
    void getChatHistory_withCursorAndSize_delegatesWithParams() throws Exception {
        ChatHistoryResponse response = new ChatHistoryResponse(Collections.emptyList(), 5L, true);
        given(chatService.getChatHistory(any(Member.class), eq(1L), eq(10L), eq(5))).willReturn(response);

        mockMvc.perform(get("/api/chat/rooms/{roomId}/messages", 1L)
                        .param("cursorId", "10")
                        .param("size", "5")
                        .with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextCursorId", is(5)))
                .andExpect(jsonPath("$.data.hasNext", is(true)));
    }

    @Test
    @DisplayName("updateLastReadMessage는 서비스에 위임하고 200을 반환한다")
    void updateLastReadMessage_delegatesToService() throws Exception {
        mockMvc.perform(post("/api/chat/rooms/{roomId}/read", 1L).with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        verify(chatService).updateLastReadMessage(any(Member.class), eq(1L));
    }
}
