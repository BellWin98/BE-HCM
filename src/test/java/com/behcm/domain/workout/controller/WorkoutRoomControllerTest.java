package com.behcm.domain.workout.controller;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.workout.dto.CreateWorkoutRoomRequest;
import com.behcm.domain.workout.dto.JoinWorkoutRoomByCodeRequest;
import com.behcm.domain.workout.dto.SchedulePenaltyChangeRequest;
import com.behcm.domain.workout.dto.WorkoutRoomDetailResponse;
import com.behcm.domain.workout.dto.WorkoutRoomResponse;
import com.behcm.domain.workout.service.WorkoutRoomService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkoutRoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WorkoutRoomService workoutRoomService;

    private Member member() {
        return Member.builder()
                .email("user@test.com")
                .password("encoded")
                .nickname("user")
                .role(MemberRole.USER)
                .build();
    }

    private WorkoutRoomResponse roomResponse() {
        return WorkoutRoomResponse.builder()
                .id(1L)
                .name("Room 1")
                .minWeeklyWorkouts(3)
                .maxMembers(10)
                .currentMembers(1)
                .isActive(true)
                .build();
    }

    private CreateWorkoutRoomRequest createRoomRequest() {
        CreateWorkoutRoomRequest request = new CreateWorkoutRoomRequest();
        request.setName("Room 1");
        request.setMinWeeklyWorkouts(3);
        request.setPenaltyEnabled(false);
        request.setMaxMembers(10);
        request.setEntryCode("ENTRY01");
        return request;
    }

    @Test
    @DisplayName("createWorkoutRoom은 인증 없이 요청하면 401을 반환한다")
    void createWorkoutRoom_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/workout/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRoomRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("createWorkoutRoom은 벌금제도를 켰는데 회당 벌금이 없으면 400을 반환한다")
    void createWorkoutRoom_penaltyEnabledWithoutAmount_returnsBadRequest() throws Exception {
        CreateWorkoutRoomRequest request = createRoomRequest();
        request.setPenaltyEnabled(true);
        request.setPenaltyPerMiss(null);

        mockMvc.perform(post("/api/workout/rooms")
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("createWorkoutRoom은 유효한 요청이면 생성된 방을 반환한다")
    void createWorkoutRoom_validRequest_returnsCreatedRoom() throws Exception {
        given(workoutRoomService.createWorkoutRoom(any(Member.class), any(CreateWorkoutRoomRequest.class)))
                .willReturn(roomResponse());

        mockMvc.perform(post("/api/workout/rooms")
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRoomRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("Room 1")));
    }

    @Test
    @DisplayName("getJoinedWorkoutRooms는 인증된 사용자가 참여한 방 목록을 반환한다")
    void getJoinedWorkoutRooms_returnsJoinedRooms() throws Exception {
        given(workoutRoomService.getJoinedWorkoutRooms(any(Member.class))).willReturn(List.of(roomResponse()));

        mockMvc.perform(get("/api/workout/rooms/joined").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name", is("Room 1")));
    }

    @Test
    @DisplayName("getJoinedWorkoutRoom은 방 상세 정보를 반환한다")
    void getJoinedWorkoutRoom_returnsRoomDetail() throws Exception {
        WorkoutRoomDetailResponse detail = new WorkoutRoomDetailResponse(roomResponse(), Collections.emptyList(), null);
        given(workoutRoomService.getJoinedWorkoutRoom(eq(1L), any(Member.class))).willReturn(detail);

        mockMvc.perform(get("/api/workout/rooms/joined/{roomId}", 1L).with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workoutRoomInfo.name", is("Room 1")));
    }

    @Test
    @DisplayName("getWorkoutRooms는 /api/workout/rooms/** 전체가 인증 필요 경로라 인증 없이 요청하면 401을 반환한다")
    void getWorkoutRooms_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/workout/rooms"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("getWorkoutRooms는 인증된 사용자에게 전체 운동방 목록을 반환한다")
    void getWorkoutRooms_authenticated_returnsAllRooms() throws Exception {
        given(workoutRoomService.getWorkoutRooms()).willReturn(List.of(roomResponse()));

        mockMvc.perform(get("/api/workout/rooms").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name", is("Room 1")));
    }

    @Test
    @DisplayName("joinWorkoutRoomByCode는 입장코드 형식이 올바르지 않으면 400을 반환한다")
    void joinWorkoutRoomByCode_invalidCodeFormat_returnsBadRequest() throws Exception {
        Map<String, String> request = Map.of("entryCode", "!!");

        mockMvc.perform(post("/api/workout/rooms/join")
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("joinWorkoutRoomByCode는 유효한 코드면 참여한 방을 반환한다")
    void joinWorkoutRoomByCode_validCode_returnsJoinedRoom() throws Exception {
        given(workoutRoomService.joinWorkoutRoomByCode(eq("ENTRY01"), any(Member.class)))
                .willReturn(roomResponse());
        JoinWorkoutRoomByCodeRequest request = new JoinWorkoutRoomByCodeRequest();
        setField(request, "entryCode", "ENTRY01");

        mockMvc.perform(post("/api/workout/rooms/join")
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("Room 1")));
    }

    @Test
    @DisplayName("regenerateEntryCode는 갱신된 입장코드가 담긴 방 정보를 반환한다")
    void regenerateEntryCode_returnsUpdatedRoom() throws Exception {
        given(workoutRoomService.regenerateEntryCode(eq(1L), any(Member.class))).willReturn(roomResponse());

        mockMvc.perform(post("/api/workout/rooms/{roomId}/regenerate-entry-code", 1L).with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("Room 1")));
    }

    @Test
    @DisplayName("schedulePenaltyChange는 전환 예정일이 없으면 400을 반환한다")
    void schedulePenaltyChange_missingEffectiveDate_returnsBadRequest() throws Exception {
        SchedulePenaltyChangeRequest request = new SchedulePenaltyChangeRequest();
        request.setPenaltyEnabled(false);

        mockMvc.perform(patch("/api/workout/rooms/{roomId}/penalty-schedule", 1L)
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("schedulePenaltyChange는 유효한 요청이면 서비스에 위임한다")
    void schedulePenaltyChange_validRequest_delegatesToService() throws Exception {
        SchedulePenaltyChangeRequest request = new SchedulePenaltyChangeRequest();
        request.setPenaltyEnabled(false);
        request.setEffectiveDate(java.time.LocalDate.now().plusDays(14)
                .with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY)));
        given(workoutRoomService.scheduleOwnerPenaltyChange(eq(1L), any(SchedulePenaltyChangeRequest.class), any(Member.class)))
                .willReturn(roomResponse());

        mockMvc.perform(patch("/api/workout/rooms/{roomId}/penalty-schedule", 1L)
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("Room 1")));
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
