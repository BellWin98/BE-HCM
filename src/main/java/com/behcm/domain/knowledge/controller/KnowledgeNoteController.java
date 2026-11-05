package com.behcm.domain.knowledge.controller;

import com.behcm.domain.knowledge.dto.KnowledgeNoteImageUploadResponse;
import com.behcm.domain.knowledge.dto.KnowledgeNoteRequest;
import com.behcm.domain.knowledge.dto.KnowledgeNoteResponse;
import com.behcm.domain.knowledge.entity.KnowledgeCategory;
import com.behcm.domain.knowledge.service.KnowledgeNoteService;
import com.behcm.domain.member.entity.Member;
import com.behcm.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 지식 메모 CRUD·검색 API. 접근 권한은 {@code KnowledgeAccessChecker} 가 판정한다.
 *
 * <p>{@code @PreAuthorize} 를 클래스 레벨에 두는 것은 의도적이다: 엔드포인트가 추가돼도 기본이 차단이어야 한다.
 * 권한 확인용 {@code /access} 는 403 을 받으면 안 되므로 {@link KnowledgeAccessController} 에 따로 둔다.
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge-notes")
@RequiredArgsConstructor
@PreAuthorize("@knowledgeAccessChecker.canAccess(principal)")
public class KnowledgeNoteController {

    private final KnowledgeNoteService knowledgeNoteService;

    @PostMapping
    public ResponseEntity<ApiResponse<KnowledgeNoteResponse>> create(
            @AuthenticationPrincipal Member member,
            @Valid @RequestBody KnowledgeNoteRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("메모를 저장했습니다.",
                knowledgeNoteService.create(member, request)));
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<KnowledgeNoteImageUploadResponse>> uploadImages(
            @AuthenticationPrincipal Member member,
            @RequestPart("files") List<MultipartFile> files
    ) {
        List<String> urls = knowledgeNoteService.uploadImages(files);
        log.info("Knowledge note images uploaded (memberId={}, count={})", member.getId(), urls.size());
        return ResponseEntity.ok(ApiResponse.success("이미지를 업로드했습니다.",
                new KnowledgeNoteImageUploadResponse(urls)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<KnowledgeNoteResponse>>> search(
            @AuthenticationPrincipal Member member,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) KnowledgeCategory category,
            @RequestParam(required = false) String stockSymbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        // TODO(Task 6): scopes 쿼리 파라미터를 받아 넘긴다. 빈 집합은 기본 범위다.
        return ResponseEntity.ok(ApiResponse.success(
                knowledgeNoteService.search(member, keyword, category, stockSymbol, Set.of(), page, size)));
    }

    @GetMapping("/stock-counts")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStockCounts(
            @AuthenticationPrincipal Member member,
            @RequestParam List<String> symbols
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                knowledgeNoteService.countByStockSymbols(member, symbols)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<KnowledgeNoteResponse>> get(
            @AuthenticationPrincipal Member member,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(knowledgeNoteService.get(member, id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<KnowledgeNoteResponse>> update(
            @AuthenticationPrincipal Member member,
            @PathVariable Long id,
            @Valid @RequestBody KnowledgeNoteRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("메모를 수정했습니다.",
                knowledgeNoteService.update(member, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Member member,
            @PathVariable Long id
    ) {
        knowledgeNoteService.delete(member, id);
        return ResponseEntity.ok(ApiResponse.success("메모를 삭제했습니다.", null));
    }
}
