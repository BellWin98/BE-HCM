package com.behcm.domain.stats.controller;

import com.behcm.domain.stats.dto.LandingStatsResponse;
import com.behcm.domain.stats.service.StatsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatsService statsService;

    @Test
    @DisplayName("getLandingSummary는 인증 없이도(permitAll) 통계를 반환한다")
    void getLandingSummary_withoutAuthentication_returnsStats() throws Exception {
        given(statsService.getLandingStats()).willReturn(LandingStatsResponse.of(120L, 4500L, 37L));

        mockMvc.perform(get("/api/stats/landing-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalUsers", is(120)))
                .andExpect(jsonPath("$.data.totalExerciseProofs", is(4500)))
                .andExpect(jsonPath("$.data.activeRooms", is(37)));
    }
}
