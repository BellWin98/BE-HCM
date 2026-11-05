package com.behcm.domain.knowledge.service;

import com.behcm.domain.knowledge.dto.KnowledgeNoteRequest;
import com.behcm.domain.knowledge.dto.KnowledgeNoteResponse;
import com.behcm.domain.knowledge.entity.KnowledgeCategory;
import com.behcm.domain.knowledge.entity.KnowledgeNote;
import com.behcm.domain.knowledge.repository.KnowledgeNoteRepository;
import com.behcm.domain.knowledge.repository.KnowledgeNoteScope;
import com.behcm.domain.knowledge.repository.KnowledgeNoteSpecs;
import com.behcm.domain.knowledge.repository.StockNoteCount;
import com.behcm.domain.member.entity.Member;
import com.behcm.global.config.aws.S3Service;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 지식 메모 CRUD·검색. 모든 조회·변경은 요청한 회원 본인의 메모로 한정하며,
 * 남의 메모는 존재 여부를 숨기기 위해 NOT_FOUND 로 응답한다. 접근 권한(ADMIN) 판정은 컨트롤러 책임이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KnowledgeNoteService {

    private static final int MAX_SEARCH_TERMS = 5;
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_COUNT_SYMBOLS = 100;
    /** 검색어 구분자. Java 정규식 \s 는 ASCII 공백만 잡으므로 전각 공백(U+3000)을 따로 넣는다. */
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u3000]+");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final KnowledgeNoteRepository knowledgeNoteRepository;
    private final S3Service s3Service;
    private final KnowledgeNoteImageCleaner imageCleaner;

    @Transactional
    public KnowledgeNoteResponse create(Member member, KnowledgeNoteRequest request) {
        NormalizedNote n = normalize(request);
        KnowledgeNote note = KnowledgeNote.builder()
                .member(member)
                .category(n.category())
                .content(n.content())
                .sourceUrl(n.sourceUrl())
                .sourceText(n.sourceText())
                .stockSymbol(n.stockSymbol())
                .stockName(n.stockName())
                .build();
        note.replaceTags(n.tags());
        note.replaceImages(n.imageUrls());
        KnowledgeNote saved = knowledgeNoteRepository.save(note);
        log.info("Knowledge note created (memberId={}, noteId={})", member.getId(), saved.getId());
        return KnowledgeNoteResponse.from(saved);
    }

    /** 메모에 붙일 이미지를 S3 에 올리고 URL 을 돌려준다. 장수(1~5)는 S3 호출 전에 확인한다. */
    public List<String> uploadImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.size() > KnowledgeNote.MAX_IMAGES) {
            throw new CustomException(ErrorCode.KNOWLEDGE_NOTE_IMAGE_INVALID);
        }
        return s3Service.uploadKnowledgeNoteImages(files);
    }

    public KnowledgeNoteResponse get(Member member, Long id) {
        return KnowledgeNoteResponse.from(findOwned(member, id));
    }

    @Transactional
    public KnowledgeNoteResponse update(Member member, Long id, KnowledgeNoteRequest request) {
        KnowledgeNote note = findOwned(member, id);
        NormalizedNote n = normalize(request);
        note.update(n.category(), n.content(), n.sourceUrl(), n.sourceText(), n.stockSymbol(), n.stockName(),
                note.getVisibility());
        note.replaceTags(n.tags());
        List<String> removedImages = note.replaceImages(n.imageUrls());
        // updatedAt 은 flush 시점에 갱신되므로, 응답을 만들기 전에 flush 해서 최신 값을 반영한다.
        knowledgeNoteRepository.flush();
        imageCleaner.deleteAfterCommit(removedImages);
        log.info("Knowledge note updated (memberId={}, noteId={})", member.getId(), id);
        return KnowledgeNoteResponse.from(note);
    }

    @Transactional
    public void delete(Member member, Long id) {
        KnowledgeNote note = findOwned(member, id);
        List<String> imageUrls = note.getImageUrls();
        knowledgeNoteRepository.delete(note);
        imageCleaner.deleteAfterCommit(imageUrls);
        log.info("Knowledge note deleted (memberId={}, noteId={})", member.getId(), id);
    }

    public Page<KnowledgeNoteResponse> search(Member member, String keyword, KnowledgeCategory category,
                                              String stockSymbol, int page, int size) {
        int safeSize = Math.min(Math.max(size, MIN_PAGE_SIZE), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize, DEFAULT_SORT);
        // TODO(Task 4): 요청자 권한·scopes 로 범위를 정한다. 그때까지는 기존처럼 본인 메모만 조회한다.
        return knowledgeNoteRepository
                .findAll(KnowledgeNoteSpecs.visibleTo(member.getId(), KnowledgePermissions.ADMIN,
                                Set.of(KnowledgeNoteScope.MINE))
                        .and(KnowledgeNoteSpecs.matches(category, blankToNull(stockSymbol), splitTerms(keyword))),
                        pageable)
                .map(KnowledgeNoteResponse::from);
    }

    public Map<String, Long> countByStockSymbols(Member member, List<String> symbols) {
        if (symbols == null) {
            return Map.of();
        }
        if (symbols.size() > MAX_COUNT_SYMBOLS) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String symbol : symbols) {
            String trimmed = blankToNull(symbol);
            if (trimmed != null) {
                distinct.add(trimmed);
            }
        }
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> result = new HashMap<>();
        for (StockNoteCount row : knowledgeNoteRepository.countVisibleByStockSymbols(member.getId(), false, distinct)) {
            result.put(row.stockSymbol(), row.count());
        }
        return result;
    }

    private KnowledgeNote findOwned(Member member, Long id) {
        // TODO(Task 4): 권한에 따라 공개 메모 조회·수정 가능 여부를 판정한다. 그때까지는 본인 메모만.
        return knowledgeNoteRepository.findVisibleById(id, member.getId(), false)
                .orElseThrow(() -> new CustomException(ErrorCode.KNOWLEDGE_NOTE_NOT_FOUND));
    }

    /** 생성·수정 공통 정규화 및 검증. 요청 DTO 는 형식(길이·필수)만 보고, 의미 검증은 여기서 한다. */
    private NormalizedNote normalize(KnowledgeNoteRequest request) {
        String sourceUrl = blankToNull(request.getSourceUrl());
        String sourceText = blankToNull(request.getSourceText());
        if (sourceUrl != null && !isHttpUrl(sourceUrl)) {
            throw new CustomException(ErrorCode.KNOWLEDGE_NOTE_SOURCE_URL_INVALID);
        }

        String stockSymbol = blankToNull(request.getStockSymbol());
        String stockName = blankToNull(request.getStockName());
        if (request.getCategory() != KnowledgeCategory.STOCK && (stockSymbol != null || stockName != null)) {
            throw new CustomException(ErrorCode.KNOWLEDGE_NOTE_STOCK_CATEGORY_MISMATCH);
        }
        if (stockSymbol == null) {
            stockName = null;
        } else if (stockName == null) {
            stockName = stockSymbol;
        }

        String content = blankToNull(request.getContent());
        List<String> imageUrls = normalizeImageUrls(request.getImageUrls());
        if (content == null && imageUrls.isEmpty()) {
            throw new CustomException(ErrorCode.KNOWLEDGE_NOTE_CONTENT_OR_IMAGE_REQUIRED);
        }

        return new NormalizedNote(request.getCategory(), content, sourceUrl, sourceText,
                stockSymbol, stockName, normalizeTags(request.getTags()), imageUrls);
    }

    /** 이 앱이 올린 이미지(knowledge-notes/ 아래)만, 중복 없이 최대 {@link KnowledgeNote#MAX_IMAGES}장까지 허용한다. */
    private List<String> normalizeImageUrls(List<String> rawUrls) {
        if (rawUrls == null) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        for (String raw : rawUrls) {
            String url = blankToNull(raw);
            if (url != null) {
                urls.add(url);
            }
        }
        if (urls.size() > KnowledgeNote.MAX_IMAGES
                || new LinkedHashSet<>(urls).size() != urls.size()
                || !urls.stream().allMatch(s3Service::isKnowledgeNoteImageUrl)) {
            throw new CustomException(ErrorCode.KNOWLEDGE_NOTE_IMAGE_INVALID);
        }
        return urls;
    }

    private List<String> normalizeTags(List<String> rawTags) {
        if (rawTags == null) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> tags = new ArrayList<>();
        for (String raw : rawTags) {
            String tag = blankToNull(raw);
            if (tag != null && seen.add(tag.toLowerCase(Locale.ROOT))) {
                tags.add(tag);
            }
        }
        if (tags.size() > KnowledgeNote.MAX_TAGS
                || tags.stream().anyMatch(tag -> tag.length() > KnowledgeNote.MAX_TAG_LENGTH)) {
            throw new CustomException(ErrorCode.KNOWLEDGE_NOTE_TAG_INVALID);
        }
        return tags;
    }

    private List<String> splitTerms(String keyword) {
        String trimmed = blankToNull(keyword);
        if (trimmed == null) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        for (String term : WHITESPACE.split(trimmed)) {
            if (!term.isBlank() && terms.size() < MAX_SEARCH_TERMS) {
                terms.add(term);
            }
        }
        return terms;
    }

    private boolean isHttpUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /** strip() 은 전각 공백(U+3000) 같은 유니코드 공백도 지운다 — trim() 은 ASCII 제어 문자·공백만 지운다. */
    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private record NormalizedNote(KnowledgeCategory category, String content, String sourceUrl, String sourceText,
                                  String stockSymbol, String stockName, List<String> tags,
                                  List<String> imageUrls) {
    }
}
