package com.behcm.domain.knowledge.service;

import com.behcm.domain.knowledge.dto.KnowledgeNoteRequest;
import com.behcm.domain.knowledge.dto.KnowledgeNoteResponse;
import com.behcm.domain.knowledge.entity.KnowledgeCategory;
import com.behcm.domain.knowledge.entity.KnowledgeNote;
import com.behcm.domain.knowledge.repository.KnowledgeNoteRepository;
import com.behcm.domain.knowledge.repository.KnowledgeNoteSpecs;
import com.behcm.domain.knowledge.repository.StockNoteCount;
import com.behcm.domain.member.entity.Member;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final KnowledgeNoteRepository knowledgeNoteRepository;

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
        KnowledgeNote saved = knowledgeNoteRepository.save(note);
        log.info("Knowledge note created (memberId={}, noteId={})", member.getId(), saved.getId());
        return KnowledgeNoteResponse.from(saved);
    }

    public KnowledgeNoteResponse get(Member member, Long id) {
        return KnowledgeNoteResponse.from(findOwned(member, id));
    }

    @Transactional
    public KnowledgeNoteResponse update(Member member, Long id, KnowledgeNoteRequest request) {
        KnowledgeNote note = findOwned(member, id);
        NormalizedNote n = normalize(request);
        note.update(n.category(), n.content(), n.sourceUrl(), n.sourceText(), n.stockSymbol(), n.stockName());
        note.replaceTags(n.tags());
        // updatedAt 은 flush 시점에 갱신되므로, 응답을 만들기 전에 flush 해서 최신 값을 반영한다.
        knowledgeNoteRepository.flush();
        log.info("Knowledge note updated (memberId={}, noteId={})", member.getId(), id);
        return KnowledgeNoteResponse.from(note);
    }

    @Transactional
    public void delete(Member member, Long id) {
        KnowledgeNote note = findOwned(member, id);
        knowledgeNoteRepository.delete(note);
        log.info("Knowledge note deleted (memberId={}, noteId={})", member.getId(), id);
    }

    public Page<KnowledgeNoteResponse> search(Member member, String keyword, KnowledgeCategory category,
                                              String stockSymbol, int page, int size) {
        int safeSize = Math.min(Math.max(size, MIN_PAGE_SIZE), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize, DEFAULT_SORT);
        return knowledgeNoteRepository
                .findAll(KnowledgeNoteSpecs.search(member.getId(), category, blankToNull(stockSymbol),
                        splitTerms(keyword)), pageable)
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
        for (StockNoteCount row : knowledgeNoteRepository.countByStockSymbols(member.getId(), distinct)) {
            result.put(row.stockSymbol(), row.count());
        }
        return result;
    }

    private KnowledgeNote findOwned(Member member, Long id) {
        return knowledgeNoteRepository.findByIdAndMemberId(id, member.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.KNOWLEDGE_NOTE_NOT_FOUND));
    }

    /** 생성·수정 공통 정규화 및 검증. 요청 DTO 는 형식(길이·필수)만 보고, 의미 검증은 여기서 한다. */
    private NormalizedNote normalize(KnowledgeNoteRequest request) {
        String sourceUrl = blankToNull(request.getSourceUrl());
        String sourceText = blankToNull(request.getSourceText());
        if (sourceUrl == null && sourceText == null) {
            throw new CustomException(ErrorCode.KNOWLEDGE_NOTE_SOURCE_REQUIRED);
        }
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

        return new NormalizedNote(request.getCategory(), request.getContent(), sourceUrl, sourceText,
                stockSymbol, stockName, normalizeTags(request.getTags()));
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
        for (String term : trimmed.split("\\s+")) {
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

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record NormalizedNote(KnowledgeCategory category, String content, String sourceUrl, String sourceText,
                                  String stockSymbol, String stockName, List<String> tags) {
    }
}
