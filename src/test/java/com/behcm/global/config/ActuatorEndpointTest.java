package com.behcm.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ActuatorEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("/actuator/health 는 인증 없이도(permitAll) UP 상태를 반환한다")
    void health_withoutAuthentication_returnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("/actuator/prometheus 는 인증 없이도(permitAll) Prometheus 텍스트 포맷을 반환한다")
    void prometheus_withoutAuthentication_returnsMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"));
    }

    @Test
    @DisplayName("management.endpoints.web.exposure.include 에 없는 엔드포인트(env 등)는 디스커버리 목록에 없다")
    void discoveryPage_onlyListsIncludedEndpoints() throws Exception {
        mockMvc.perform(get("/actuator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.health").exists())
                .andExpect(jsonPath("$._links.prometheus").exists())
                .andExpect(jsonPath("$._links.env").doesNotExist())
                .andExpect(jsonPath("$._links.beans").doesNotExist());
    }
}
