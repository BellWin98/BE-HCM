package com.behcm.domain.knowledge.entity;

import com.behcm.domain.member.entity.Member;
import com.behcm.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 관리자 전용 지식 메모. 소유 범위는 작성한 회원 본인이다(별도 owner 개념 없음).
 *
 * <p>태그 정규화(trim/중복 제거/개수·길이 검증)는 서비스 책임이며, {@link #replaceTags} 는 받은 이름으로 교체한다.
 */
@Entity
@Table(
        name = "knowledge_note",
        indexes = {
                @Index(name = "idx_knowledge_note_member_created", columnList = "member_id, createdAt"),
                @Index(name = "idx_knowledge_note_member_stock", columnList = "member_id, stockSymbol")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnowledgeNote extends BaseTimeEntity {

    public static final int MAX_CONTENT_LENGTH = 2000;
    public static final int MAX_SOURCE_URL_LENGTH = 2048;
    public static final int MAX_SOURCE_TEXT_LENGTH = 500;
    public static final int MAX_IMAGES = 5;
    public static final int MAX_TAGS = 10;
    public static final int MAX_TAG_LENGTH = 30;
    public static final int MAX_STOCK_SYMBOL_LENGTH = 20;
    public static final int MAX_STOCK_NAME_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KnowledgeCategory category;

    @Column(columnDefinition = "text")
    private String content;

    @Column(length = MAX_SOURCE_URL_LENGTH)
    private String sourceUrl;

    @Column(length = MAX_SOURCE_TEXT_LENGTH)
    private String sourceText;

    @Column(length = MAX_STOCK_SYMBOL_LENGTH)
    private String stockSymbol;

    @Column(length = MAX_STOCK_NAME_LENGTH)
    private String stockName;

    @OneToMany(mappedBy = "note", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<KnowledgeNoteTag> tags = new ArrayList<>();

    @OneToMany(mappedBy = "note", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<KnowledgeNoteImage> images = new ArrayList<>();

    @Builder
    public KnowledgeNote(Member member, KnowledgeCategory category, String content, String sourceUrl,
                         String sourceText, String stockSymbol, String stockName) {
        this.member = member;
        this.category = category;
        this.content = content;
        this.sourceUrl = sourceUrl;
        this.sourceText = sourceText;
        this.stockSymbol = stockSymbol;
        this.stockName = stockName;
    }

    public void update(KnowledgeCategory category, String content, String sourceUrl, String sourceText,
                       String stockSymbol, String stockName) {
        this.category = category;
        this.content = content;
        this.sourceUrl = sourceUrl;
        this.sourceText = sourceText;
        this.stockSymbol = stockSymbol;
        this.stockName = stockName;
    }

    /**
     * 태그를 소문자 이름 기준 diff 로 교체한다. 기존 행은 유지(철자·대소문자만 바뀌면 이름만 갱신)하고,
     * 빠진 것만 지우고 새 것만 추가한다.
     *
     * <p>{@code clear()} 후 전부 새로 넣으면 Hibernate 가 IDENTITY insert 를 orphan delete 보다 먼저 실행해,
     * 유지한 태그나 대소문자만 바꾼 태그(utf8mb4_unicode_ci 에서 같은 값)가 UK 에 걸린다.
     */
    public void replaceTags(List<String> names) {
        Map<String, String> wanted = new LinkedHashMap<>();
        for (String name : names) {
            wanted.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
        }
        Iterator<KnowledgeNoteTag> it = tags.iterator();
        while (it.hasNext()) {
            KnowledgeNoteTag tag = it.next();
            String newName = wanted.remove(tag.getName().toLowerCase(Locale.ROOT));
            if (newName == null) {
                it.remove();
            } else {
                tag.rename(newName);
            }
        }
        for (String name : wanted.values()) {
            tags.add(new KnowledgeNoteTag(this, name));
        }
    }

    public List<String> getTagNames() {
        return tags.stream().map(KnowledgeNoteTag::getName).toList();
    }

    /**
     * 이미지를 URL 기준 diff 로 교체하고 빠진 URL 목록을 돌려준다(저장소 파일 정리용).
     * 유지되는 이미지는 position 만 갱신하고, 빠진 것만 지우고 새 것만 추가한다.
     * 개수·형식 검증은 서비스 책임이다.
     *
     * <p>{@link #replaceTags} 와 같은 이유로 {@code clear()} 후 재삽입하지 않는다 —
     * Hibernate 가 IDENTITY insert 를 orphan delete 보다 먼저 실행한다.
     */
    public List<String> replaceImages(List<String> urls) {
        Map<String, Integer> wanted = new LinkedHashMap<>();
        for (String url : urls) {
            wanted.putIfAbsent(url, wanted.size());
        }
        List<String> removed = new ArrayList<>();
        Iterator<KnowledgeNoteImage> it = images.iterator();
        while (it.hasNext()) {
            KnowledgeNoteImage image = it.next();
            Integer position = wanted.remove(image.getImageUrl());
            if (position == null) {
                removed.add(image.getImageUrl());
                it.remove();
            } else {
                image.changePosition(position);
            }
        }
        wanted.forEach((url, position) -> images.add(new KnowledgeNoteImage(this, url, position)));
        // 새 행이 뒤에 붙으므로, 같은 트랜잭션 안에서 읽어도 position 순이 되도록 정렬해 둔다.
        images.sort(Comparator.comparingInt(KnowledgeNoteImage::getPosition));
        return removed;
    }

    public List<String> getImageUrls() {
        return images.stream().map(KnowledgeNoteImage::getImageUrl).toList();
    }
}
