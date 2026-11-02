package com.behcm.domain.knowledge.entity;

import com.behcm.domain.member.entity.Member;
import com.behcm.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 전용 지식 메모. 소유 범위는 작성한 회원 본인이다(별도 owner 개념 없음).
 *
 * <p>태그 정규화(trim/중복 제거/개수·길이 검증)는 서비스 책임이며, {@link #replaceTags} 는 받은 값 그대로 교체한다.
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

    @Column(nullable = false, columnDefinition = "text")
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

    public void replaceTags(List<String> names) {
        tags.clear();
        for (String name : names) {
            tags.add(new KnowledgeNoteTag(this, name));
        }
    }

    public List<String> getTagNames() {
        return tags.stream().map(KnowledgeNoteTag::getName).toList();
    }
}
