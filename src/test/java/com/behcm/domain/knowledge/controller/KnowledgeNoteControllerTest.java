package com.behcm.domain.knowledge.controller;

import com.behcm.domain.knowledge.dto.KnowledgeNoteRequest;
import com.behcm.domain.knowledge.dto.KnowledgeNoteResponse;
import com.behcm.domain.knowledge.entity.KnowledgeCategory;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인가 판정 규칙 자체는 KnowledgeAccessCheckerTest 가 담당한다. 여기서는 checker 를 목으로 두고
 * 허용/차단이 컨트롤러에 반영되는지, 요청 바인딩이 서비스 호출로 이어지는지만 본다.
 */
class KnowledgeNoteControllerTest extends IntegrationTestSupport {

    private Member member(MemberRole role) {
        return Member.builder()
                .email("user@test.com")
                .password("encoded")
                .nickname("user")
                .role(role)
                .build();
    }

    private Member grantedMember() {
        given(knowledgeAccessChecker.canAccess(any())).willReturn(true);
        return member(MemberRole.ADMIN);
    }

    private Member deniedMember() {
        given(knowledgeAccessChecker.canAccess(any())).willReturn(false);
        return member(MemberRole.FAMILY);
    }

    private KnowledgeNoteResponse response(Long id) {
        return new KnowledgeNoteResponse(id, KnowledgeCategory.STOCK, "내용", null, null,
                List.of("반도체"), "005930", "삼성전자", LocalDateTime.now(), LocalDateTime.now());
    }

    private String validBody() throws Exception {
        KnowledgeNoteRequest request = new KnowledgeNoteRequest();
        request.setCategory(KnowledgeCategory.STOCK);
        request.setContent("내용");
        return objectMapper.writeValueAsString(request);
    }

    @Test
    @DisplayName("지식 메모 API는 인증 없이 요청하면 401을 반환한다")
    void anyEndpoint_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/knowledge-notes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/knowledge-notes/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/knowledge-notes")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/knowledge-notes/stock-counts").param("symbols", "005930"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("지식 메모 API는 checker 가 거부하면 403을 반환하고 서비스를 호출하지 않는다")
    void anyEndpoint_whenCheckerDenies_returns403() throws Exception {
        Member denied = deniedMember();

        mockMvc.perform(post("/api/knowledge-notes").with(user(denied))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/knowledge-notes").with(user(denied)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/knowledge-notes/stock-counts").param("symbols", "005930")
                        .with(user(denied)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(knowledgeNoteService);
    }

    @Test
    @DisplayName("create는 내용이 비어 있으면 400을 반환한다")
    void create_withBlankContent_returns400() throws Exception {
        KnowledgeNoteRequest request = new KnowledgeNoteRequest();
        request.setCategory(KnowledgeCategory.STOCK);
        request.setContent("  ");

        mockMvc.perform(post("/api/knowledge-notes").with(user(grantedMember()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(knowledgeNoteService);
    }

    @Test
    @DisplayName("create는 저장된 메모를 반환한다")
    void create_returnsSavedNote() throws Exception {
        given(knowledgeNoteService.create(any(), any(KnowledgeNoteRequest.class))).willReturn(response(7L));

        mockMvc.perform(post("/api/knowledge-notes").with(user(grantedMember()))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("메모를 저장했습니다.")))
                .andExpect(jsonPath("$.data.id", is(7)));
    }

    @Test
    @DisplayName("search는 쿼리 파라미터를 서비스에 그대로 전달한다")
    void search_passesQueryParamsToService() throws Exception {
        given(knowledgeNoteService.search(any(), any(), any(), any(), eq(0), eq(20)))
                .willReturn(new PageImpl<>(List.of(response(1L)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/knowledge-notes").with(user(grantedMember()))
                        .param("keyword", "삼성 반도체")
                        .param("category", "STOCK")
                        .param("stockSymbol", "005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id", is(1)));

        verify(knowledgeNoteService).search(any(), eq("삼성 반도체"), eq(KnowledgeCategory.STOCK),
                eq("005930"), eq(0), eq(20));
    }

    @Test
    @DisplayName("get/update/delete는 경로의 id 를 서비스에 전달한다")
    void getUpdateDelete_passPathId() throws Exception {
        Member granted = grantedMember();
        given(knowledgeNoteService.get(any(), eq(3L))).willReturn(response(3L));
        given(knowledgeNoteService.update(any(), eq(3L), any(KnowledgeNoteRequest.class)))
                .willReturn(response(3L));

        mockMvc.perform(get("/api/knowledge-notes/3").with(user(granted)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(3)));
        mockMvc.perform(put("/api/knowledge-notes/3").with(user(granted))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("메모를 수정했습니다.")));
        mockMvc.perform(delete("/api/knowledge-notes/3").with(user(granted)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("메모를 삭제했습니다.")));

        verify(knowledgeNoteService).delete(any(), eq(3L));
    }

    @Test
    @DisplayName("stock-counts는 쉼표로 구분된 symbols 를 리스트로 바인딩한다")
    void stockCounts_bindsCommaSeparatedSymbols() throws Exception {
        given(knowledgeNoteService.countByStockSymbols(any(), any()))
                .willReturn(Map.of("005930", 2L, "AAPL", 1L));

        mockMvc.perform(get("/api/knowledge-notes/stock-counts").with(user(grantedMember()))
                        .param("symbols", "005930,AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.005930", is(2)));

        verify(knowledgeNoteService).countByStockSymbols(any(), eq(List.of("005930", "AAPL")));
    }

    @Test
    @DisplayName("access 엔드포인트는 {id} 경로에 가려지지 않아 권한이 없어도 200을 반환한다")
    void access_endpoint_isNotShadowedBy_idPath() throws Exception {
        given(knowledgeAccessChecker.canAccess(any())).willReturn(false);

        mockMvc.perform(get("/api/knowledge-notes/access").with(user(member(MemberRole.FAMILY))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasAccess", is(false)));
    }
}
