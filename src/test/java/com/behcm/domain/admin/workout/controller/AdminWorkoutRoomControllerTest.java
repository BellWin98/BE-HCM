package com.behcm.domain.admin.workout.controller;

import com.behcm.domain.admin.workout.dto.AdminUpdateRoomRequest;
import com.behcm.domain.admin.workout.service.AdminWorkoutRoomService;
import com.behcm.domain.workout.dto.WorkoutRoomDetailResponse;
import com.behcm.domain.workout.dto.WorkoutRoomResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminWorkoutRoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminWorkoutRoomService adminWorkoutRoomService;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN 권한으로 운동방 목록 조회 시 200과 ApiResponse.success가 반환된다")
    void getRooms_withAdminRole_returnsOk() throws Exception {
        // given
        WorkoutRoomResponse roomResponse = WorkoutRoomResponse.builder()
                .id(1L)
                .name("Room 1")
                .minWeeklyWorkouts(3)
                .penaltyPerMiss(1000L)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(10))
                .maxMembers(10)
                .currentMembers(1)
                .isActive(true)
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<WorkoutRoomResponse> page =
                new PageImpl<>(List.of(roomResponse), pageable, 1);

        given(adminWorkoutRoomService.getRooms(anyString(), anyBoolean(), any(Pageable.class)))
                .willReturn(page);

        // when & then
        mockMvc.perform(get("/api/admin/workout/rooms")
                        .param("query", "Room")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content[0].name", is("Room 1")));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ADMIN 권한이 아닌 사용자로 운동방 목록 조회 시 403이 반환된다")
    void getRooms_withNonAdminRole_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/workout/rooms"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN 권한으로 운동방 상세 조회 시 200과 ApiResponse.success가 반환된다")
    void getRoomDetail_withAdminRole_returnsOk() throws Exception {
        // given
        WorkoutRoomResponse roomInfo = WorkoutRoomResponse.builder()
                .id(1L)
                .name("Room 1")
                .minWeeklyWorkouts(3)
                .penaltyPerMiss(1000L)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(10))
                .maxMembers(10)
                .currentMembers(1)
                .isActive(true)
                .build();

        WorkoutRoomDetailResponse detailResponse =
                new WorkoutRoomDetailResponse(roomInfo, Collections.emptyList(), null);

        given(adminWorkoutRoomService.getRoomDetail(anyLong()))
                .willReturn(detailResponse);

        // when & then
        mockMvc.perform(get("/api/admin/workout/rooms/{roomId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.workoutRoomInfo.name", is("Room 1")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN 권한으로 운동방 설정 수정 시 200과 수정된 정보가 반환된다")
    void updateRoomSettings_withAdminRole_returnsOk() throws Exception {
        // given
        AdminUpdateRoomRequest request = new AdminUpdateRoomRequest();
        request.setStartDate(LocalDate.now().plusDays(1));
        request.setEndDate(LocalDate.now().plusDays(10));
        request.setMaxMembers(20);
        request.setMinWeeklyWorkouts(5);
        request.setPenaltyPerMiss(2000L);

        WorkoutRoomResponse roomResponse = WorkoutRoomResponse.builder()
                .id(1L)
                .name("Room 1")
                .minWeeklyWorkouts(5)
                .penaltyPerMiss(2000L)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .maxMembers(20)
                .currentMembers(1)
                .isActive(true)
                .build();

        given(adminWorkoutRoomService.updateRoomSettings(anyLong(), any(AdminUpdateRoomRequest.class)))
                .willReturn(roomResponse);

        // when & then
        mockMvc.perform(put("/api/admin/workout/rooms/{roomId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.minWeeklyWorkouts", is(5)))
                .andExpect(jsonPath("$.data.maxMembers", is(20)));
    }
}

