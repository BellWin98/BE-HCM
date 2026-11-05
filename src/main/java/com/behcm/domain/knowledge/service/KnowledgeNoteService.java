package com.behcm.domain.knowledge.service;

import com.behcm.domain.knowledge.dto.KnowledgeNoteRequest;
import com.behcm.domain.knowledge.dto.KnowledgeNoteResponse;
import com.behcm.domain.knowledge.entity.KnowledgeCategory;
import com.behcm.domain.knowledge.entity.KnowledgeNote;
import com.behcm.domain.knowledge.entity.KnowledgeVisibility;
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
 * 지식 메모 CRUD·검색. 조회 범위와 수정 가능 여부는 요청자의 {@link KnowledgePermissions} 로 정한다.
 *
 * <ul>
 *   <li>ADMIN: 모든 메모를 조회·수정·삭제한다.</li>
 *   <li>조회 권한: 본인 메모 + 다른 회원의 공개 메모를 조회한다.</li>
 *   <li>작성 권한: 본인 메모를 만들고, 본인 메모만 수정·삭제한다.</li>
 * </ul>
 *
 * <p>다른 회원의 비공개 메모는 ADMIN 이 아니면 어떤 경로로도 보이지 않으며, 존재 여부를 숨기기 위해
 * NOT_FOUND 로 응답한다. 보이지만 수정할 수 없는 메모(남의 공개 메모)는 FORBIDDEN 이다.
 * 메뉴·API 진입 가능 여부는 컨트롤러가 {@link KnowledgeAccessChecker} 로 판정한다.
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
    private final KnowledgeAccessChecker accessChecker;

    @Transactional
    public KnowledgeNoteResponse create(Member member, KnowledgeNoteRequest request) {
        KnowledgePermissions p = accessChecker.permissionsOf(member);
        NormalizedNote n = normalize(request);
        KnowledgeNote note = KnowledgeNote.builder()
                .member(member)
                .category(n.category())
                .content(n.content())
                .sourceUrl(n.sourceUrl())
                .sourceText(n.sourceText())
                .stockSymbol(n.stockSymbol())
                .stockName(n.stockName())
                .visibility(request.getVisibility() != null ? request.getVisibility() : KnowledgeVisibility.PUBLIC)
                .build();
        note.replaceTags(n.tags());
        note.replaceImages(n.imageUrls());
        KnowledgeNote saved = knowledgeNoteRepository.save(note);
        log.info("Knowledge note created (memberId={}, noteId={})", member.getId(), saved.getId());
        return KnowledgeNoteResponse.of(saved, member.getId(), p);
    }

    /** 메모에 붙일 이미지를 S3 에 올리고 URL 을 돌려준다. 장수(1~5)는 S3 호출 전에 확인한다. */
    public List<String> uploadImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.size() > KnowledgeNote.MAX_IMAGES) {
            throw new CustomException(ErrorCode.KNOWLEDGE_NOTE_IMAGE_INVALID);
        }
        return s3Service.uploadKnowledgeNoteImages(files);
    }

    public KnowledgeNoteResponse get(Member member, Long id) {
        KnowledgePermissions p = accessChecker.permissionsOf(member);
        return KnowledgeNoteResponse.of(findVisible(member, p, id), member.getId(), p);
    }

    @Transactional
    public KnowledgeNoteResponse update(Member member, Long id, KnowledgeNoteRequest request) {
        KnowledgePermissions p = accessChecker.permissionsOf(member);
        KnowledgeNote note = findEditable(member, p, id);
        NormalizedNote n = normalize(request);
        KnowledgeVisibility visibility = request.getVisibility() != null ? request.getVisibility() : note.getVisibility();
        // 작성자(member)는 바꾸지 않는다 — ADMIN 이 고쳐도 원 작성자의 메모로 남는다.
        note.update(n.category(), n.content(), n.sourceUrl(), n.sourceText(), n.stockSymbol(), n.stockName(),
                visibility);
        note.replaceTags(n.tags());
        List<String> removedImages = note.replaceImages(n.imageUrls());
        // updatedAt 은 flush 시점에 갱신되므로, 응답을 만들기 전에 flush 해서 최신 값을 반영한다.
        knowledgeNoteRepository.flush();
        imageCleaner.deleteAfterCommit(removedImages);
        log.info("Knowledge note updated (actorId={}, authorId={}, noteId={})",
                member.getId(), note.getMember().getId(), id);
        return KnowledgeNoteResponse.of(note, member.getId(), p);
    }

    @Transactional
    public void delete(Member member, Long id) {
        KnowledgePermissions p = accessChecker.permissionsOf(member);
        KnowledgeNote note = findEditable(member, p, id);
        Long authorId = note.getMember().getId();
        List<String> imageUrls = note.getImageUrls();
        knowledgeNoteRepository.delete(note);
        imageCleaner.deleteAfterCommit(imageUrls);
        log.info("Knowledge note deleted (actorId={}, authorId={}, noteId={})", member.getId(), authorId, id);
    }

    public Page<KnowledgeNoteResponse> search(Member member, String keyword, KnowledgeCategory category,
                                              String stockSymbol, Set<KnowledgeNoteScope> scopes,
                                              int page, int size) {
        KnowledgePermissions p = accessChecker.permissionsOf(member);
        Long me = member.getId();
        int safeSize = Math.min(Math.max(size, MIN_PAGE_SIZE), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize, DEFAULT_SORT);
        return knowledgeNoteRepository
                .findAll(KnowledgeNoteSpecs.visibleTo(me, p, scopes)
                        .and(KnowledgeNoteSpecs.matches(category, blankToNull(stockSymbol), splitTerms(keyword))),
                        pageable)
                .map(note -> KnowledgeNoteResponse.of(note, me, p));
    }

    public Map<String, Long> countByStockSymbols(Member member, List<String> symbols) {
        KnowledgePermissions p = accessChecker.permissionsOf(member);
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
        for (StockNoteCount row : knowledgeNoteRepository.countVisibleByStockSymbols(member.getId(), p.seesOthersPublic(), distinct)) {
            result.put(row.stockSymbol(), row.count());
        }
        return result;
    }

    /** 요청자에게 보이는 메모. ADMIN 은 모든 메모, 그 외에는 본인 메모(+조회 권한이면 공개 메모). */
    private KnowledgeNote findVisible(Member member, KnowledgePermissions p, Long id) {
        return (p.admin()
                ? knowledgeNoteRepository.findById(id)
                : knowledgeNoteRepository.findVisibleById(id, member.getId(), p.seesOthersPublic()))
                .orElseThrow(() -> new CustomException(ErrorCode.KNOWLEDGE_NOTE_NOT_FOUND));
    }

    /** 수정·삭제할 메모. 보이지 않으면 NOT_FOUND, 보이지만 고칠 수 없으면 FORBIDDEN. */
    private KnowledgeNote findEditable(Member member, KnowledgePermissions p, Long id) {
        KnowledgeNote note = findVisible(member, p, id);
        if (!p.canEdit(note, member.getId())) {
            throw new CustomException(ErrorCode.KNOWLEDGE_NOTE_FORBIDDEN);
        }
        return note;
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
