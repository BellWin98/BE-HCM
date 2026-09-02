package com.behcm.domain.tossstock.controller;

import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.tossstock.service.TossAccessChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이 엔드포인트는 권한 없는 회원도 200 으로 답해야 한다 — 403 으로 막으면 프론트가
 * "권한 없음"과 "서버 오류"를 구분할 수 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TossAccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TossAccessChecker tossAccessChecker;

    private Member member() {
        return Member.builder()
                .email("user@test.com")
                .password("encoded")
                .nickname("user")
                .role(MemberRole.USER)
                .build();
    }

    @Test
    @DisplayName("getMyAccess는 인증 없이 요청하면 401을 반환한다")
    void getMyAccess_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/toss-stock/access"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("getMyAccess는 권한이 있으면 hasAccess=true 를 반환한다")
    void getMyAccess_withTossAccess_returnsTrue() throws Exception {
        given(tossAccessChecker.canAccess(any())).willReturn(true);

        mockMvc.perform(get("/api/toss-stock/access").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasAccess", is(true)));
    }

    @Test
    @DisplayName("getMyAccess는 권한이 없어도 200으로 hasAccess=false 를 반환한다")
    void getMyAccess_withoutTossAccess_returnsFalseNotForbidden() throws Exception {
        given(tossAccessChecker.canAccess(any())).willReturn(false);

        mockMvc.perform(get("/api/toss-stock/access").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasAccess", is(false)));
    }
}
