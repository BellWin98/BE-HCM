package com.behcm.domain.common;

import com.behcm.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthCheckControllerTest extends IntegrationTestSupport {

    @Test
    @DisplayName("헬스체크는 인증 없이도(permitAll) 200을 반환한다")
    void healthCheck_withoutAuthentication_returnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }
}
