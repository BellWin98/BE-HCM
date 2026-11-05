package com.behcm.domain.knowledge.controller;

import com.behcm.domain.knowledge.service.KnowledgePermissions;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
class KnowledgeAccessControllerTest extends IntegrationTestSupport {

    private Member member() {
        return Member.builder()
                .email("user@test.com")
                .password("encoded")
                .nickname("user")
                .role(MemberRole.USER)
                .build();
    }

    @Test
    @DisplayName("getAccess는 인증 없이 요청하면 401을 반환한다")
    void getAccess_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/knowledge-notes/access"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("getAccess는 권한이 없어도 200으로 판정 결과(false)를 반환한다")
    void getAccess_returnsCheckerResult() throws Exception {
        given(knowledgeAccessChecker.permissionsOf(any())).willReturn(KnowledgePermissions.NONE);

        mockMvc.perform(get("/api/knowledge-notes/access").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasAccess", is(false)));
    }

    @Test
    @DisplayName("getAccess는 hasAccess/canRead/canWrite/isAdmin 을 모두 반환한다")
    void getAccess_returnsAllFlags() throws Exception {
        given(knowledgeAccessChecker.permissionsOf(any())).willReturn(new KnowledgePermissions(false, true, false));

        mockMvc.perform(get("/api/knowledge-notes/access").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasAccess", is(true)))
                .andExpect(jsonPath("$.data.canRead", is(true)))
                .andExpect(jsonPath("$.data.canWrite", is(false)))
                .andExpect(jsonPath("$.data.isAdmin", is(false)));
    }
}
