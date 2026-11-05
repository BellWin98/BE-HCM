package com.behcm.domain.admin.knowledgeaccess.controller;

import com.behcm.domain.admin.knowledgeaccess.dto.AdminKnowledgeAccessRequest;
import com.behcm.domain.admin.knowledgeaccess.dto.AdminKnowledgeAccessResponse;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminKnowledgeAccessControllerTest extends IntegrationTestSupport {

    private Member member(MemberRole role) {
        return Member.builder()
                .email("user@test.com")
                .password("encoded")
                .nickname("user")
                .role(role)
                .build();
    }

    @Test
    @DisplayName("USER 권한으로 조회·변경하면 403을 반환한다")
    void getAndPut_asUser_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/knowledge-access").with(user(member(MemberRole.USER))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/admin/knowledge-access/2").with(user(member(MemberRole.USER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canRead\":true,\"canWrite\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN 의 PUT 은 서비스에 위임한다")
    void put_asAdmin_delegatesToService() throws Exception {
        given(adminKnowledgeAccessService.update(eq(2L), any(AdminKnowledgeAccessRequest.class), any()))
                .willReturn(AdminKnowledgeAccessResponse.builder()
                        .memberId(2L).canRead(true).canWrite(false).build());

        mockMvc.perform(put("/api/admin/knowledge-access/2").with(user(member(MemberRole.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canRead\":true,\"canWrite\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("지식 메모 권한을 변경했습니다.")))
                .andExpect(jsonPath("$.data.memberId", is(2)))
                .andExpect(jsonPath("$.data.canRead", is(true)));

        verify(adminKnowledgeAccessService).update(eq(2L), eq(new AdminKnowledgeAccessRequest(true, false)), any());
    }

    @Test
    @DisplayName("필드가 누락되면 400을 반환한다")
    void put_missingField_returns400() throws Exception {
        mockMvc.perform(put("/api/admin/knowledge-access/2").with(user(member(MemberRole.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canRead\":true}"))
                .andExpect(status().isBadRequest());
    }
}
