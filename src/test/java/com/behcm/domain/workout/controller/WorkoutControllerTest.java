package com.behcm.domain.workout.controller;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.workout.dto.WorkoutRequest;
import com.behcm.domain.workout.dto.WorkoutResponse;
import com.behcm.domain.workout.service.WorkoutService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkoutService workoutService;

    private Member member() {
        return Member.builder()
                .email("user@test.com")
                .password("encoded")
                .nickname("user")
                .role(MemberRole.USER)
                .build();
    }

    private MockMultipartFile image(String name) {
        return new MockMultipartFile("images", name, "image/png", new byte[]{1, 2, 3});
    }

    @Test
    @DisplayName("authenticateWorkout은 인증 없이 요청하면 401을 반환한다")
    void authenticateWorkout_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(multipart("/api/workouts")
                        .file(image("a.png"))
                        .param("workoutDate", "2026-07-19")
                        .param("workoutTypes", "헬스")
                        .param("duration", "60"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("authenticateWorkout은 이미지가 3개를 초과하면 COUNT_LIMIT_EXCEEDED로 400을 반환한다")
    void authenticateWorkout_tooManyImages_returnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/workouts")
                        .file(image("a.png"))
                        .file(image("b.png"))
                        .file(image("c.png"))
                        .file(image("d.png"))
                        .param("workoutDate", "2026-07-19")
                        .param("workoutTypes", "헬스")
                        .param("duration", "60")
                        .with(user(member())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("업로드 가능한 파일 개수를 초과했습니다.")));

        verify(workoutService, never()).authenticateWorkout(any(), any());
    }

    @Test
    @DisplayName("authenticateWorkout은 운동 종류가 3개를 초과하면 COUNT_LIMIT_EXCEEDED로 400을 반환한다")
    void authenticateWorkout_tooManyWorkoutTypes_returnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/workouts")
                        .file(image("a.png"))
                        .param("workoutDate", "2026-07-19")
                        .param("workoutTypes", "헬스", "러닝", "수영", "요가")
                        .param("duration", "60")
                        .with(user(member())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("운동 종류는 최대 3개까지 등록할 수 있습니다.")));

        verify(workoutService, never()).authenticateWorkout(any(), any());
    }

    @Test
    @DisplayName("authenticateWorkout은 유효한 요청이면 서비스에 위임하고 200을 반환한다")
    void authenticateWorkout_validRequest_returnsOk() throws Exception {
        WorkoutResponse response = new WorkoutResponse(
                LocalDate.parse("2026-07-19"), List.of("헬스"), 60, List.of("https://s3/a.png"), 10);
        given(workoutService.authenticateWorkout(any(Member.class), any(WorkoutRequest.class)))
                .willReturn(response);

        mockMvc.perform(multipart("/api/workouts")
                        .file(image("a.png"))
                        .param("workoutDate", "2026-07-19")
                        .param("workoutTypes", "헬스")
                        .param("duration", "60")
                        .with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberTotalWorkoutDays", is(10)))
                .andExpect(jsonPath("$.data.imageUrls[0]", is("https://s3/a.png")));
    }
}
