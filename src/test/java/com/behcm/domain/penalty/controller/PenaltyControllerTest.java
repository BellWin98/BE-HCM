package com.behcm.domain.penalty.controller;

import com.behcm.domain.penalty.dto.PenaltyAccountInfo;
import com.behcm.domain.penalty.dto.PenaltyAccountRequest;
import com.behcm.domain.penalty.dto.PenaltyRecord;
import com.behcm.domain.penalty.service.PenaltyService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PenaltyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PenaltyService penaltyService;

    private com.behcm.domain.member.entity.Member member() {
        return com.behcm.domain.member.entity.Member.builder()
                .email("user@test.com")
                .password("encoded")
                .nickname("user")
                .role(com.behcm.domain.member.entity.MemberRole.USER)
                .build();
    }

    @Test
    @DisplayName("upsertPenaltyAccount는 인증 없이 요청하면 401을 반환한다")
    void upsertPenaltyAccount_withoutAuthentication_returnsUnauthorized() throws Exception {
        PenaltyAccountRequest request = new PenaltyAccountRequest();
        request.setBankName("국민은행");
        request.setAccountNumber("123-456");
        request.setAccountHolder("홍길동");

        mockMvc.perform(post("/api/penalty/rooms/{roomId}/account", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("upsertPenaltyAccount는 등록/수정된 계좌 정보를 반환한다")
    void upsertPenaltyAccount_validRequest_returnsAccountInfo() throws Exception {
        PenaltyAccountRequest request = new PenaltyAccountRequest();
        request.setBankName("국민은행");
        request.setAccountNumber("123-456");
        request.setAccountHolder("홍길동");

        PenaltyAccountInfo response = PenaltyAccountInfo.builder()
                .id(1L)
                .bankName("국민은행")
                .accountNumber("123-456")
                .accountHolder("홍길동")
                .build();
        given(penaltyService.upsertPenaltyAccount(eq(1L), any(PenaltyAccountRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/penalty/rooms/{roomId}/account", 1L)
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bankName", is("국민은행")));
    }

    @Test
    @DisplayName("getPenaltyAccount는 등록된 계좌 정보를 반환한다")
    void getPenaltyAccount_returnsAccountInfo() throws Exception {
        PenaltyAccountInfo response = PenaltyAccountInfo.builder()
                .id(1L)
                .bankName("국민은행")
                .accountNumber("123-456")
                .accountHolder("홍길동")
                .build();
        given(penaltyService.getPenaltyAccount(1L)).willReturn(response);

        mockMvc.perform(get("/api/penalty/rooms/{roomId}/account", 1L).with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountHolder", is("홍길동")));
    }

    @Test
    @DisplayName("deletePenaltyAccount는 서비스에 위임하고 200을 반환한다")
    void deletePenaltyAccount_delegatesToService() throws Exception {
        mockMvc.perform(delete("/api/penalty/rooms/{roomId}/account", 1L).with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        verify(penaltyService).deletePenaltyAccount(1L);
    }

    @Test
    @DisplayName("getPenaltyRecords는 기간 파라미터 없이 요청하면 null로 전달한다")
    void getPenaltyRecords_withoutDateParams_passesNulls() throws Exception {
        PenaltyRecord record = PenaltyRecord.builder()
                .id(1L)
                .workoutRoomMemberId(1L)
                .requiredWorkouts(3)
                .actualWorkouts(1)
                .penaltyAmount(10000L)
                .isPaid(false)
                .build();
        given(penaltyService.getPenaltyRecords(eq(1L), isNull(), isNull())).willReturn(List.of(record));

        mockMvc.perform(get("/api/penalty/rooms/{roomId}/records", 1L).with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].penaltyAmount", is(10000)));
    }

    @Test
    @DisplayName("getPenaltyRecords는 기간 파라미터를 파싱해 서비스에 전달한다")
    void getPenaltyRecords_withDateParams_parsesAndDelegates() throws Exception {
        given(penaltyService.getPenaltyRecords(
                eq(1L), eq(LocalDate.parse("2026-07-01")), eq(LocalDate.parse("2026-07-31"))))
                .willReturn(List.of());

        mockMvc.perform(get("/api/penalty/rooms/{roomId}/records", 1L)
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31")
                        .with(user(member())))
                .andExpect(status().isOk());

        verify(penaltyService).getPenaltyRecords(1L, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"));
    }
}
