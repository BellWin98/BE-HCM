package com.behcm.domain.admin.member.controller;

import com.behcm.domain.admin.member.dto.AdminMemberResponse;
import com.behcm.domain.admin.member.dto.UpdateMemberRoleRequest;
import com.behcm.domain.admin.member.service.AdminMemberService;
import com.behcm.domain.member.entity.MemberRole;
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

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminMemberService adminMemberService;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN 권한으로 회원 목록 조회 시 200과 ApiResponse.success가 반환된다")
    void getMembers_withAdminRole_returnsOk() throws Exception {
        // given
        AdminMemberResponse memberResponse = AdminMemberResponse.builder()
                .id(1L)
                .email("admin@example.com")
                .nickname("admin")
                .role(MemberRole.ADMIN)
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<AdminMemberResponse> page =
                new PageImpl<>(List.of(memberResponse), pageable, 1);

        given(adminMemberService.getMembers(anyString(), any(), any(Pageable.class)))
                .willReturn(page);

        // when & then
        mockMvc.perform(get("/api/admin/members")
                        .param("query", "admin")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content[0].email", is("admin@example.com")))
                .andExpect(jsonPath("$.data.content[0].role", is("ADMIN")));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ADMIN 권한이 아닌 사용자로 회원 목록 조회 시 403이 반환된다")
    void getMembers_withNonAdminRole_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/members"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("인증되지 않은 사용자로 회원 목록 조회 시 401이 반환된다")
    void getMembers_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/members"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN 권한으로 회원 역할 변경 시 200과 변경된 역할이 반환된다")
    void updateMemberRole_withAdminRole_returnsOk() throws Exception {
        // given
        UpdateMemberRoleRequest request = new UpdateMemberRoleRequest();
        request.setRole(MemberRole.ADMIN);

        AdminMemberResponse response = AdminMemberResponse.builder()
            .id(1L)
            .email("user@example.com")
            .nickname("user")
            .role(MemberRole.ADMIN)
            .build();

        given(adminMemberService.updateMemberRole(eq(1L), eq(MemberRole.ADMIN)))
                .willReturn(response);

        // when & then
        mockMvc.perform(patch("/api/admin/members/{memberId}/role", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.role", is("ADMIN")));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ADMIN 권한이 아닌 사용자로 회원 역할 변경 시 403이 반환된다")
    void updateMemberRole_withNonAdminRole_returnsForbidden() throws Exception {
        UpdateMemberRoleRequest request = new UpdateMemberRoleRequest();
        request.setRole(MemberRole.ADMIN);

        mockMvc.perform(patch("/api/admin/members/{memberId}/role", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}

