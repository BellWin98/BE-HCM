package com.behcm.domain.admin.tossaccess.controller;

import com.behcm.domain.admin.tossaccess.dto.AdminTossAccessResponse;
import com.behcm.domain.admin.tossaccess.service.AdminTossAccessService;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
class AdminTossAccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminTossAccessService adminTossAccessService;

    private Member member(MemberRole role) {
        return Member.builder()
                .email("user@test.com")
                .password("encoded")
                .nickname("user")
                .role(role)
                .build();
    }

    @Test
    @DisplayName("인증 없이 요청하면 401을 반환한다")
    void getGrantedMembers_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/toss-access"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("USER 권한으로 요청하면 403을 반환한다")
    void getGrantedMembers_withUserRole_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/toss-access").with(user(member(MemberRole.USER))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FAMILY 권한으로도 접근할 수 없다")
    void getGrantedMembers_withFamilyRole_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/toss-access").with(user(member(MemberRole.FAMILY))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN은 권한이 부여된 회원 목록을 조회할 수 있다")
    void getGrantedMembers_withAdminRole_returnsList() throws Exception {
        given(adminTossAccessService.getGrantedMembers()).willReturn(List.of(
                AdminTossAccessResponse.builder()
                        .memberId(2L)
                        .email("target@example.com")
                        .nickname("target")
                        .grantedBy(1L)
                        .build()
        ));

        mockMvc.perform(get("/api/admin/toss-access").with(user(member(MemberRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].memberId", is(2)))
                .andExpect(jsonPath("$.data[0].nickname", is("target")));
    }

    @Test
    @DisplayName("ADMIN은 회원에게 토스 접근 권한을 부여할 수 있다")
    void grant_withAdminRole_returnsOk() throws Exception {
        given(adminTossAccessService.grant(eq(2L), any())).willReturn(
                AdminTossAccessResponse.builder()
                        .memberId(2L)
                        .email("target@example.com")
                        .nickname("target")
                        .build()
        );

        mockMvc.perform(post("/api/admin/toss-access/2").with(user(member(MemberRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId", is(2)));
    }

    @Test
    @DisplayName("USER는 토스 접근 권한을 부여할 수 없다")
    void grant_withUserRole_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/admin/toss-access/2").with(user(member(MemberRole.USER))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN은 토스 접근 권한을 회수할 수 있다")
    void revoke_withAdminRole_returnsOk() throws Exception {
        mockMvc.perform(delete("/api/admin/toss-access/2").with(user(member(MemberRole.ADMIN))))
                .andExpect(status().isOk());

        verify(adminTossAccessService).revoke(2L);
    }
}
