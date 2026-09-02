package com.behcm.domain.social.controller;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.social.dto.CommentRequest;
import com.behcm.domain.social.dto.CommentResponse;
import com.behcm.domain.social.dto.ReactionCountResponse;
import com.behcm.domain.social.dto.ReactionRequest;
import com.behcm.domain.social.dto.WorkoutSocialSummary;
import com.behcm.domain.social.entity.ReactionEmoji;
import com.behcm.domain.social.service.WorkoutCommentService;
import com.behcm.domain.social.service.WorkoutReactionService;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkoutSocialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WorkoutReactionService workoutReactionService;

    @MockitoBean
    private WorkoutCommentService workoutCommentService;

    private Member member() {
        return Member.builder()
                .email("user@test.com")
                .password("encoded")
                .nickname("user")
                .role(MemberRole.USER)
                .build();
    }

    private ReactionRequest reactionRequest(String emoji) {
        ReactionRequest request = new ReactionRequest();
        request.setEmoji(emoji);
        return request;
    }

    private CommentRequest commentRequest(String content) {
        CommentRequest request = new CommentRequest();
        request.setContent(content);
        return request;
    }

    @Test
    @DisplayName("react는 인증 없이 요청하면 401을 반환한다")
    void react_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/workouts/10/reactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reactionRequest("MUSCLE"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("react는 갱신된 리액션 집계를 반환한다")
    void react_returnsUpdatedSummary() throws Exception {
        WorkoutSocialSummary summary = WorkoutSocialSummary.builder()
                .reactions(List.of(ReactionCountResponse.of(ReactionEmoji.MUSCLE, 3L, true)))
                .commentCount(2L)
                .build();
        given(workoutReactionService.react(any(Member.class), eq(10L), any(ReactionRequest.class))).willReturn(summary);

        mockMvc.perform(post("/api/workouts/10/reactions")
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reactionRequest("MUSCLE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reactions[0].emoji", is("MUSCLE")))
                .andExpect(jsonPath("$.data.reactions[0].symbol", is(ReactionEmoji.MUSCLE.getSymbol())))
                .andExpect(jsonPath("$.data.reactions[0].count", is(3)))
                .andExpect(jsonPath("$.data.reactions[0].reactedByMe", is(true)))
                .andExpect(jsonPath("$.data.commentCount", is(2)));
    }

    @Test
    @DisplayName("react는 이모지가 비어 있으면 400을 반환한다")
    void react_blankEmoji_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/workouts/10/reactions")
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reactionRequest(""))))
                .andExpect(status().isBadRequest());

        verify(workoutReactionService, never()).react(any(), any(), any());
    }

    @Test
    @DisplayName("react는 지원하지 않는 이모지면 400을 반환한다")
    void react_unsupportedEmoji_returnsBadRequest() throws Exception {
        willThrow(new CustomException(ErrorCode.UNSUPPORTED_REACTION))
                .given(workoutReactionService).react(any(Member.class), eq(10L), any(ReactionRequest.class));

        mockMvc.perform(post("/api/workouts/10/reactions")
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reactionRequest("ROCKET"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is(ErrorCode.UNSUPPORTED_REACTION.getMessage())));
    }

    @Test
    @DisplayName("cancelReaction은 서비스에 위임한다")
    void cancelReaction_delegatesToService() throws Exception {
        given(workoutReactionService.cancelReaction(any(Member.class), eq(10L)))
                .willReturn(WorkoutSocialSummary.empty());

        mockMvc.perform(delete("/api/workouts/10/reactions").with(user(member())))
                .andExpect(status().isOk());

        verify(workoutReactionService).cancelReaction(any(Member.class), eq(10L));
    }

    @Test
    @DisplayName("addComment는 작성된 댓글을 반환한다")
    void addComment_returnsCreatedComment() throws Exception {
        given(workoutCommentService.addComment(any(Member.class), eq(10L), any(CommentRequest.class)))
                .willReturn(CommentResponse.builder()
                        .id(1L)
                        .nickname("user")
                        .content("화이팅!")
                        .mine(true)
                        .build());

        mockMvc.perform(post("/api/workouts/10/comments")
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commentRequest("화이팅!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", is("화이팅!")))
                .andExpect(jsonPath("$.data.mine", is(true)));
    }

    @Test
    @DisplayName("addComment는 내용이 비어 있으면 400을 반환한다")
    void addComment_blankContent_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/workouts/10/comments")
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commentRequest("   "))))
                .andExpect(status().isBadRequest());

        verify(workoutCommentService, never()).addComment(any(), any(), any());
    }

    @Test
    @DisplayName("addComment는 500자를 넘으면 400을 반환한다")
    void addComment_tooLongContent_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/workouts/10/comments")
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commentRequest("가".repeat(501)))))
                .andExpect(status().isBadRequest());

        verify(workoutCommentService, never()).addComment(any(), any(), any());
    }

    @Test
    @DisplayName("deleteComment는 작성자가 아니면 403을 반환한다")
    void deleteComment_notAuthor_returnsForbidden() throws Exception {
        willThrow(new CustomException(ErrorCode.NOT_COMMENT_AUTHOR))
                .given(workoutCommentService).deleteComment(any(Member.class), eq(10L), eq(100L));

        mockMvc.perform(delete("/api/workouts/10/comments/100").with(user(member())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is(ErrorCode.NOT_COMMENT_AUTHOR.getMessage())));
    }

    @Test
    @DisplayName("getComments는 운동방 멤버가 아니면 403을 반환한다")
    void getComments_notRoomMember_returnsForbidden() throws Exception {
        given(workoutCommentService.getComments(any(Member.class), eq(10L), eq(0), eq(20)))
                .willThrow(new CustomException(ErrorCode.NOT_WORKOUT_ROOM_MEMBER));

        mockMvc.perform(get("/api/workouts/10/comments").with(user(member())))
                .andExpect(status().isForbidden());
    }
}
