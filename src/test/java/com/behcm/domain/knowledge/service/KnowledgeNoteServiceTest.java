package com.behcm.domain.knowledge.service;

import com.behcm.domain.knowledge.dto.KnowledgeNoteRequest;
import com.behcm.domain.knowledge.dto.KnowledgeNoteResponse;
import com.behcm.domain.knowledge.entity.KnowledgeCategory;
import com.behcm.domain.knowledge.entity.KnowledgeNote;
import com.behcm.domain.knowledge.repository.KnowledgeNoteRepository;
import com.behcm.domain.knowledge.repository.StockNoteCount;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.global.exception.CustomException;
import com.behcm.global.config.aws.S3Service;
import com.behcm.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeNoteServiceTest {

    @Mock
    private KnowledgeNoteRepository repository;

    @Mock
    private S3Service s3Service;

    @Mock
    private KnowledgeNoteImageCleaner imageCleaner;

    @InjectMocks
    private KnowledgeNoteService service;

    private Member member;

    @BeforeEach
    void setUp() {
        member = Member.builder().email("a@b.c").password("p").nickname("n").role(MemberRole.ADMIN).build();
        ReflectionTestUtils.setField(member, "id", 1L);
    }

    private KnowledgeNoteRequest request(KnowledgeCategory category, String sourceUrl, String sourceText) {
        KnowledgeNoteRequest r = new KnowledgeNoteRequest();
        r.setCategory(category);
        r.setContent("내용");
        r.setSourceUrl(sourceUrl);
        r.setSourceText(sourceText);
        return r;
    }

    private static String img(String name) {
        return "https://bucket.s3.ap-northeast-2.amazonaws.com/knowledge-notes/" + name + ".png";
    }

    private void allowAllImageUrls() {
        // 개수·중복 검사에서 먼저 걸리는 테스트도 있어 호출되지 않을 수 있다.
        org.mockito.Mockito.lenient().when(s3Service.isKnowledgeNoteImageUrl(any())).thenReturn(true);
    }

    private KnowledgeNote saveAndCapture(KnowledgeNoteRequest r) {
        when(repository.save(any(KnowledgeNote.class))).thenAnswer(i -> i.getArgument(0));
        service.create(member, r);
        ArgumentCaptor<KnowledgeNote> captor = ArgumentCaptor.forClass(KnowledgeNote.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(code);
    }

    @Test
    @DisplayName("출처가 둘 다 없어도 저장되고, 공백 출처는 null 로 정리한다")
    void create_withoutSource_succeeds() {
        KnowledgeNote saved = saveAndCapture(request(KnowledgeCategory.DEV, "  ", " "));

        assertThat(saved.getSourceUrl()).isNull();
        assertThat(saved.getSourceText()).isNull();
        assertThat(saved.getContent()).isEqualTo("내용");
    }

    @Test
    @DisplayName("내용이 공백뿐이고 이미지가 1장이면 content 는 null 로 저장된다")
    void create_imageOnly_storesNullContent() {
        allowAllImageUrls();
        KnowledgeNoteRequest r = request(KnowledgeCategory.DEV, null, null);
        r.setContent("  ");
        r.setImageUrls(List.of(img("a")));
        when(repository.save(any(KnowledgeNote.class))).thenAnswer(i -> i.getArgument(0));

        KnowledgeNoteResponse response = service.create(member, r);

        ArgumentCaptor<KnowledgeNote> captor = ArgumentCaptor.forClass(KnowledgeNote.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getContent()).isNull();
        assertThat(response.images()).containsExactly(img("a"));
    }

    @Test
    @DisplayName("내용도 이미지도 없으면 거부한다")
    void create_noContentNoImage_throwsContentOrImageRequired() {
        KnowledgeNoteRequest blank = request(KnowledgeCategory.DEV, null, "출처");
        blank.setContent("\u3000 ");
        assertCode(() -> service.create(member, blank), ErrorCode.KNOWLEDGE_NOTE_CONTENT_OR_IMAGE_REQUIRED);

        KnowledgeNoteRequest nulls = request(KnowledgeCategory.DEV, null, "출처");
        nulls.setContent(null);
        nulls.setImageUrls(List.of(" "));
        assertCode(() -> service.create(member, nulls), ErrorCode.KNOWLEDGE_NOTE_CONTENT_OR_IMAGE_REQUIRED);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("이미지가 6장이면 거부한다")
    void create_sixImages_throwsImageInvalid() {
        allowAllImageUrls();
        KnowledgeNoteRequest r = request(KnowledgeCategory.DEV, null, null);
        r.setImageUrls(IntStream.rangeClosed(1, 6).mapToObj(i -> img("i" + i)).toList());

        assertCode(() -> service.create(member, r), ErrorCode.KNOWLEDGE_NOTE_IMAGE_INVALID);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("이 앱이 올린 이미지가 아닌 URL 은 거부한다")
    void create_foreignUrl_throwsImageInvalid() {
        when(s3Service.isKnowledgeNoteImageUrl("https://evil.com/x.png")).thenReturn(false);
        KnowledgeNoteRequest r = request(KnowledgeCategory.DEV, null, null);
        r.setImageUrls(List.of("https://evil.com/x.png"));

        assertCode(() -> service.create(member, r), ErrorCode.KNOWLEDGE_NOTE_IMAGE_INVALID);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("중복된 이미지 URL 은 거부한다")
    void create_duplicateUrls_throwsImageInvalid() {
        allowAllImageUrls();
        KnowledgeNoteRequest r = request(KnowledgeCategory.DEV, null, null);
        r.setImageUrls(List.of(img("a"), " " + img("a") + " "));

        assertCode(() -> service.create(member, r), ErrorCode.KNOWLEDGE_NOTE_IMAGE_INVALID);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("수정에서 빠진 이미지는 커밋 후 정리 대상으로 넘기고 순서를 유지한다")
    void update_removedImages_cleanedAfterCommit() {
        allowAllImageUrls();
        KnowledgeNote note = KnowledgeNote.builder().member(member).category(KnowledgeCategory.DEV)
                .content("old").build();
        note.replaceImages(List.of(img("a"), img("b"), img("c"), img("d"), img("e")));
        when(repository.findByIdAndMemberId(5L, 1L)).thenReturn(Optional.of(note));
        KnowledgeNoteRequest r = request(KnowledgeCategory.DEV, null, null);
        r.setImageUrls(List.of(img("a"), img("c"), img("d"), img("e")));

        KnowledgeNoteResponse response = service.update(member, 5L, r);

        verify(imageCleaner).deleteAfterCommit(List.of(img("b")));
        assertThat(response.images()).containsExactly(img("a"), img("c"), img("d"), img("e"));
    }

    @Test
    @DisplayName("이미지가 없는 수정은 정리 대상이 비어 있다")
    void update_noRemovedImages_passesEmptyList() {
        KnowledgeNote note = KnowledgeNote.builder().member(member).category(KnowledgeCategory.DEV)
                .content("old").build();
        when(repository.findByIdAndMemberId(5L, 1L)).thenReturn(Optional.of(note));

        service.update(member, 5L, request(KnowledgeCategory.DEV, null, null));

        verify(imageCleaner).deleteAfterCommit(List.of());
    }

    @Test
    @DisplayName("전각 공백(U+3000)만 있는 출처·종목은 빈 값으로 보고, 값의 앞뒤 전각 공백은 지운다")
    void create_fullWidthSpaceOnlyValues_areBlank() {
        KnowledgeNoteRequest r = request(KnowledgeCategory.STOCK, "\u3000", "\u3000인프런\u3000");
        r.setStockSymbol("\u3000");
        r.setTags(List.of("\u3000", "\u3000태그\u3000"));
        KnowledgeNote saved = saveAndCapture(r);

        assertThat(saved.getSourceUrl()).isNull();
        assertThat(saved.getSourceText()).isEqualTo("인프런");
        assertThat(saved.getStockSymbol()).isNull();
        assertThat(saved.getTagNames()).containsExactly("태그");
    }

    @Test
    @DisplayName("검색어는 전각 공백(U+3000)으로도 나누고, 전각 공백만 있으면 검색어가 없다")
    @SuppressWarnings("unchecked")
    void search_splitsOnFullWidthSpace() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        try (var specs = mockStatic(com.behcm.domain.knowledge.repository.KnowledgeNoteSpecs.class)) {
            Specification<KnowledgeNote> spec = mock(Specification.class);
            specs.when(() -> com.behcm.domain.knowledge.repository.KnowledgeNoteSpecs
                    .search(any(), any(), any(), any())).thenReturn(spec);

            service.search(member, "\u3000삼성\u3000반도체 \u3000", null, "\u3000", 0, 20);
            specs.verify(() -> com.behcm.domain.knowledge.repository.KnowledgeNoteSpecs.search(
                    1L, null, null, List.of("삼성", "반도체")));

            service.search(member, "\u3000\u3000", null, null, 0, 20);
            specs.verify(() -> com.behcm.domain.knowledge.repository.KnowledgeNoteSpecs.search(
                    1L, null, null, List.of()));
        }
    }

    @Test
    @DisplayName("텍스트 출처만 있어도 저장된다")
    void create_acceptsTextOnlySource() {
        KnowledgeNote saved = saveAndCapture(request(KnowledgeCategory.DEV, "  ", " 인프런 3강 "));

        assertThat(saved.getSourceUrl()).isNull();
        assertThat(saved.getSourceText()).isEqualTo("인프런 3강");
        assertThat(saved.getMember()).isSameAs(member);
    }

    @Test
    @DisplayName("http/https 가 아닌 링크는 거부하고, 대소문자 무시 http(s) 는 허용한다")
    void create_rejectsNonHttpUrl() {
        for (String bad : List.of("javascript:alert(1)", "ftp://x", "example.com", "httpx://a")) {
            assertCode(() -> service.create(member, request(KnowledgeCategory.DEV, bad, null)),
                    ErrorCode.KNOWLEDGE_NOTE_SOURCE_URL_INVALID);
        }
        KnowledgeNote saved = saveAndCapture(request(KnowledgeCategory.DEV, "  HTTPS://example.com/a ", null));
        assertThat(saved.getSourceUrl()).isEqualTo("HTTPS://example.com/a");
    }

    @Test
    @DisplayName("주식이 아닌 분야에 종목을 연결할 수 없다")
    void create_rejectsStockOnNonStockCategory() {
        KnowledgeNoteRequest r = request(KnowledgeCategory.DEV, null, "출처");
        r.setStockSymbol("005930");

        assertCode(() -> service.create(member, r), ErrorCode.KNOWLEDGE_NOTE_STOCK_CATEGORY_MISMATCH);
    }

    @Test
    @DisplayName("종목명이 없으면 종목 코드를 종목명으로 쓰고, 공백 종목은 null 이다")
    void create_defaultsStockNameToSymbol() {
        KnowledgeNoteRequest r = request(KnowledgeCategory.STOCK, null, "출처");
        r.setStockSymbol(" AAPL ");
        r.setStockName("  ");
        KnowledgeNote saved = saveAndCapture(r);
        assertThat(saved.getStockSymbol()).isEqualTo("AAPL");
        assertThat(saved.getStockName()).isEqualTo("AAPL");
    }

    @Test
    @DisplayName("주식 분야라도 종목이 없으면 종목 필드는 null 이다")
    void create_stockCategoryWithoutSymbol_keepsStockNull() {
        KnowledgeNote saved = saveAndCapture(request(KnowledgeCategory.STOCK, null, "출처"));
        assertThat(saved.getStockSymbol()).isNull();
        assertThat(saved.getStockName()).isNull();
    }

    @Test
    @DisplayName("태그는 trim·빈값 제거·대소문자 무시 중복 제거(첫 표기 유지)로 정규화된다")
    void create_normalizesTags() {
        KnowledgeNoteRequest r = request(KnowledgeCategory.DEV, null, "출처");
        r.setTags(new ArrayList<>(List.of(" AI ", "ai", "", "  ", "투자")));
        KnowledgeNote saved = saveAndCapture(r);

        assertThat(saved.getTagNames()).containsExactly("AI", "투자");
    }

    @Test
    @DisplayName("tags 가 null 이면 빈 목록으로 처리한다")
    void create_nullTags_treatedAsEmpty() {
        KnowledgeNote saved = saveAndCapture(request(KnowledgeCategory.DEV, null, "출처"));
        assertThat(saved.getTagNames()).isEmpty();
    }

    @Test
    @DisplayName("태그가 30자 초과이거나 정규화 후 10개 초과면 거부한다")
    void create_rejectsTagOver30Chars_or_over10Tags() {
        KnowledgeNoteRequest longTag = request(KnowledgeCategory.DEV, null, "출처");
        longTag.setTags(List.of("a".repeat(31)));
        assertCode(() -> service.create(member, longTag), ErrorCode.KNOWLEDGE_NOTE_TAG_INVALID);

        KnowledgeNoteRequest many = request(KnowledgeCategory.DEV, null, "출처");
        many.setTags(IntStream.rangeClosed(1, 11).mapToObj(i -> "t" + i).toList());
        assertCode(() -> service.create(member, many), ErrorCode.KNOWLEDGE_NOTE_TAG_INVALID);

        // 중복·빈값은 정규화로 줄어드므로 통과해야 한다
        KnowledgeNoteRequest dup = request(KnowledgeCategory.DEV, null, "출처");
        List<String> tags = new ArrayList<>(IntStream.rangeClosed(1, 10).mapToObj(i -> "t" + i).toList());
        tags.add("T1");
        tags.add(" ");
        dup.setTags(tags);
        assertThat(saveAndCapture(dup).getTagNames()).hasSize(10);

        KnowledgeNoteRequest exact = request(KnowledgeCategory.DEV, null, "출처");
        exact.setTags(List.of("a".repeat(30)));
        // 30자 경계는 별도 저장 호출이 필요하므로 예외 없이 통과만 확인
        service.create(member, exact);
    }

    @Test
    @DisplayName("다른 회원의 메모 조회는 NOT_FOUND")
    void get_otherMembersNote_throwsNotFound() {
        when(repository.findByIdAndMemberId(5L, 1L)).thenReturn(Optional.empty());

        assertCode(() -> service.get(member, 5L), ErrorCode.KNOWLEDGE_NOTE_NOT_FOUND);
    }

    @Test
    @DisplayName("수정은 모든 필드와 태그를 교체한다")
    void update_replacesAllFields_andTags() {
        KnowledgeNote note = KnowledgeNote.builder().member(member).category(KnowledgeCategory.DEV)
                .content("old").sourceUrl("https://old.com").build();
        note.replaceTags(List.of("old"));
        when(repository.findByIdAndMemberId(5L, 1L)).thenReturn(Optional.of(note));

        KnowledgeNoteRequest r = request(KnowledgeCategory.STOCK, " ", " 새 출처 ");
        r.setContent("새 내용");
        r.setStockSymbol("AAPL");
        r.setTags(List.of("new", "NEW", "x"));
        KnowledgeNoteResponse response = service.update(member, 5L, r);

        assertThat(note.getCategory()).isEqualTo(KnowledgeCategory.STOCK);
        assertThat(note.getContent()).isEqualTo("새 내용");
        assertThat(note.getSourceUrl()).isNull();
        assertThat(note.getSourceText()).isEqualTo("새 출처");
        assertThat(note.getStockSymbol()).isEqualTo("AAPL");
        assertThat(note.getStockName()).isEqualTo("AAPL");
        assertThat(note.getTagNames()).containsExactly("new", "x");
        assertThat(response.tags()).containsExactly("new", "x");
    }

    @Test
    @DisplayName("수정 응답은 flush 이후의 updatedAt 을 담는다")
    void update_flushesBeforeBuildingResponse() {
        KnowledgeNote note = KnowledgeNote.builder().member(member).category(KnowledgeCategory.DEV)
                .content("old").sourceText("s").build();
        java.time.LocalDateTime flushed = java.time.LocalDateTime.of(2026, 11, 2, 10, 0);
        when(repository.findByIdAndMemberId(5L, 1L)).thenReturn(Optional.of(note));
        org.mockito.Mockito.doAnswer(i -> {
            ReflectionTestUtils.setField(note, "updatedAt", flushed);
            return null;
        }).when(repository).flush();

        KnowledgeNoteResponse response = service.update(member, 5L, request(KnowledgeCategory.DEV, null, "새 출처"));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(repository);
        order.verify(repository).findByIdAndMemberId(5L, 1L);
        order.verify(repository).flush();
        assertThat(response.updatedAt()).isEqualTo(flushed);
    }

    @Test
    @DisplayName("다른 회원의 메모 삭제는 NOT_FOUND")
    void delete_otherMembersNote_throwsNotFound() {
        when(repository.findByIdAndMemberId(5L, 1L)).thenReturn(Optional.empty());

        assertCode(() -> service.delete(member, 5L), ErrorCode.KNOWLEDGE_NOTE_NOT_FOUND);
        verify(repository, never()).delete(any(KnowledgeNote.class));
    }

    @Test
    @DisplayName("내 메모 삭제")
    void delete_ownNote_deletes() {
        KnowledgeNote note = KnowledgeNote.builder().member(member).category(KnowledgeCategory.DEV).content("c").build();
        when(repository.findByIdAndMemberId(5L, 1L)).thenReturn(Optional.of(note));

        service.delete(member, 5L);

        verify(repository).delete(note);
    }

    @Test
    @DisplayName("메모를 삭제하면 그 메모의 이미지 URL 전부를 정리 대상으로 넘긴다")
    void delete_cleansAllImages() {
        KnowledgeNote note = KnowledgeNote.builder().member(member).category(KnowledgeCategory.DEV).content("c").build();
        note.replaceImages(List.of(img("a"), img("b")));
        when(repository.findByIdAndMemberId(5L, 1L)).thenReturn(Optional.of(note));

        service.delete(member, 5L);

        verify(imageCleaner).deleteAfterCommit(List.of(img("a"), img("b")));
    }

    @Test
    @DisplayName("검색어는 공백 기준 최대 5개로 나누고 size 를 1~50 으로 보정하며 최신순 정렬한다")
    @SuppressWarnings("unchecked")
    void search_splitsKeywordIntoAtMostFiveTerms_andClampsSize() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        try (var specs = mockStatic(com.behcm.domain.knowledge.repository.KnowledgeNoteSpecs.class)) {
            Specification<KnowledgeNote> spec = mock(Specification.class);
            specs.when(() -> com.behcm.domain.knowledge.repository.KnowledgeNoteSpecs
                    .search(any(), any(), any(), any())).thenReturn(spec);

            service.search(member, "a b  c d e f g", KnowledgeCategory.DEV, " AAPL ", 2, 0);
            specs.verify(() -> com.behcm.domain.knowledge.repository.KnowledgeNoteSpecs.search(
                    1L, KnowledgeCategory.DEV, "AAPL", List.of("a", "b", "c", "d", "e")));

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(repository).findAll(any(Specification.class), pageable.capture());
            assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
            assertThat(pageable.getValue().getPageSize()).isEqualTo(1);
            assertThat(pageable.getValue().getSort())
                    .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

            service.search(member, "   ", null, " ", -3, 999);
            specs.verify(() -> com.behcm.domain.knowledge.repository.KnowledgeNoteSpecs.search(
                    1L, null, null, List.of()));
            ArgumentCaptor<Pageable> second = ArgumentCaptor.forClass(Pageable.class);
            verify(repository, org.mockito.Mockito.times(2)).findAll(any(Specification.class), second.capture());
            assertThat(second.getValue().getPageSize()).isEqualTo(50);
            assertThat(second.getValue().getPageNumber()).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("keyword 가 null 이면 검색어 없이 조회한다")
    @SuppressWarnings("unchecked")
    void search_nullKeyword_noTerms() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(service.search(member, null, null, null, 0, 20).getContent()).isEmpty();
    }

    @Test
    @DisplayName("종목 목록이 비어 있으면 쿼리 없이 빈 맵")
    void countByStockSymbols_emptyInput_returnsEmptyWithoutQuery() {
        assertThat(service.countByStockSymbols(member, List.of())).isEmpty();
        assertThat(service.countByStockSymbols(member, null)).isEmpty();
        assertThat(service.countByStockSymbols(member, List.of(" ", ""))).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("종목 100개 초과는 INVALID_INPUT")
    void countByStockSymbols_over100Symbols_throwsInvalidInput() {
        List<String> symbols = IntStream.rangeClosed(1, 101).mapToObj(i -> "S" + i).toList();

        assertCode(() -> service.countByStockSymbols(member, symbols), ErrorCode.INVALID_INPUT);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("종목별 건수를 맵으로 반환하고 없는 종목은 포함하지 않는다")
    void countByStockSymbols_returnsMap() {
        when(repository.countByStockSymbols(any(), anyCollection()))
                .thenReturn(List.of(new StockNoteCount("AAPL", 3)));

        Map<String, Long> result = service.countByStockSymbols(member, List.of(" AAPL ", "AAPL", "TSLA"));

        assertThat(result).containsExactlyEntriesOf(Map.of("AAPL", 3L));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(repository).countByStockSymbols(any(), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder("AAPL", "TSLA");
    }
}
