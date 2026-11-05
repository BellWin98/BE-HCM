package com.behcm.domain.knowledge.repository;

import com.behcm.domain.knowledge.entity.KnowledgeCategory;
import com.behcm.domain.knowledge.entity.KnowledgeNote;
import com.behcm.domain.knowledge.entity.KnowledgeVisibility;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class KnowledgeNoteRepositoryTest extends IntegrationTestSupport {

    private static final Sort NEWEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    @Autowired
    private KnowledgeNoteRepository repository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private EntityManager em;

    private Member me;
    private Member other;

    @BeforeEach
    void setUp() {
        me = saveMember("me");
        other = saveMember("other");
    }

    private Member saveMember(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        return memberRepository.save(Member.builder()
                .email(prefix + "-" + suffix + "@test.com")
                .password("pw")
                .nickname(prefix + suffix.substring(0, 4))
                .build());
    }

    private KnowledgeNote saveNote(Member owner, KnowledgeCategory category, String content, String sourceText,
                                   String stockSymbol, String stockName, String... tags) {
        KnowledgeNote note = KnowledgeNote.builder()
                .member(owner).category(category).content(content).sourceText(sourceText)
                .stockSymbol(stockSymbol).stockName(stockName)
                .build();
        note.replaceTags(List.of(tags));
        return repository.save(note);
    }

    private List<KnowledgeNote> search(Member owner, KnowledgeCategory category, String symbol, String... terms) {
        return repository.findAll(KnowledgeNoteSpecs.search(owner.getId(), category, symbol, List.of(terms)),
                PageRequest.of(0, 20, NEWEST)).getContent();
    }

    @Test
    void search_matchesAllTermsAcrossContentSourceTagsAndStockName() {
        KnowledgeNote n1 = saveNote(me, KnowledgeCategory.STOCK, "삼성전자 6만 분할매수", null, null, null, "반도체");
        KnowledgeNote n2 = saveNote(me, KnowledgeCategory.ETC, "부동산 청약", "삼성 리포트", null, null);
        em.flush();
        em.clear();

        assertThat(search(me, null, null, "삼성", "반도체")).extracting(KnowledgeNote::getId)
                .containsExactly(n1.getId());
        assertThat(search(me, null, null, "삼성")).extracting(KnowledgeNote::getId)
                .containsExactly(n2.getId(), n1.getId());
    }

    @Test
    void search_neverReturnsOtherMembersNotes() {
        KnowledgeNote mine = saveNote(me, KnowledgeCategory.STOCK, "삼성 메모", null, null, null);
        saveNote(other, KnowledgeCategory.STOCK, "삼성 다른 사람 메모", null, null, null);
        em.flush();
        em.clear();

        assertThat(search(me, null, null, "삼성")).extracting(KnowledgeNote::getId)
                .containsExactly(mine.getId());
    }

    @Test
    void search_treatsPercentAndUnderscoreLiterally() {
        KnowledgeNote percent = saveNote(me, KnowledgeCategory.STOCK, "수익률 10% 목표", null, null, null);
        saveNote(me, KnowledgeCategory.STOCK, "수익률 100 목표", null, null, null);
        KnowledgeNote underscore = saveNote(me, KnowledgeCategory.DEV, "snake_case 규칙", null, null, null);
        saveNote(me, KnowledgeCategory.DEV, "snakeXcase 규칙", null, null, null);
        em.flush();
        em.clear();

        assertThat(search(me, null, null, "10%")).extracting(KnowledgeNote::getId)
                .containsExactly(percent.getId());
        assertThat(search(me, null, null, "snake_case")).extracting(KnowledgeNote::getId)
                .containsExactly(underscore.getId());
    }

    @Test
    void search_filtersByCategoryAndStockSymbol() {
        KnowledgeNote samsung = saveNote(me, KnowledgeCategory.STOCK, "메모1", null, "005930", "삼성전자");
        saveNote(me, KnowledgeCategory.STOCK, "메모2", null, "AAPL", "애플");
        saveNote(me, KnowledgeCategory.DEV, "메모3", null, null, null);
        em.flush();
        em.clear();

        assertThat(search(me, KnowledgeCategory.STOCK, null)).hasSize(2);
        assertThat(search(me, KnowledgeCategory.DEV, null)).hasSize(1);
        assertThat(search(me, null, "005930")).extracting(KnowledgeNote::getId)
                .containsExactly(samsung.getId());
        assertThat(search(me, KnowledgeCategory.DEV, "005930")).isEmpty();
    }

    @Test
    void countByStockSymbols_groupsOwnNotesOnly() {
        saveNote(me, KnowledgeCategory.STOCK, "a", null, "005930", "삼성전자");
        saveNote(me, KnowledgeCategory.STOCK, "b", null, "005930", "삼성전자");
        saveNote(me, KnowledgeCategory.STOCK, "c", null, "AAPL", "애플");
        saveNote(other, KnowledgeCategory.STOCK, "d", null, "005930", "삼성전자");
        em.flush();
        em.clear();

        Map<String, Long> counts = repository.countByStockSymbols(me.getId(), List.of("005930", "AAPL", "TSLA"))
                .stream().collect(Collectors.toMap(StockNoteCount::stockSymbol, StockNoteCount::count));

        assertThat(counts).containsExactlyInAnyOrderEntriesOf(Map.of("005930", 2L, "AAPL", 1L));
    }

    @Test
    void deleteAllByMemberId_alsoRemovesTagsViaCascade() {
        KnowledgeNote note = saveNote(me, KnowledgeCategory.STOCK, "태그 있는 메모", null, null, null, "t1", "t2");
        KnowledgeNote otherNote = saveNote(other, KnowledgeCategory.STOCK, "남의 메모", null, null, null, "t3");
        em.flush();
        em.clear();

        repository.deleteAllByMemberId(me.getId());
        em.flush();
        em.clear();

        assertThat(repository.findById(note.getId())).isEmpty();
        assertThat(repository.findById(otherNote.getId())).isPresent();
        Number myTags = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM knowledge_note_tag WHERE note_id = :id")
                .setParameter("id", note.getId()).getSingleResult();
        Number otherTags = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM knowledge_note_tag WHERE note_id = :id")
                .setParameter("id", otherNote.getId()).getSingleResult();
        assertThat(myTags.longValue()).isZero();
        assertThat(otherTags.longValue()).isEqualTo(1L);
    }

    private List<String> storedTagNames(Long noteId) {
        @SuppressWarnings("unchecked")
        List<String> names = em.createNativeQuery(
                "SELECT name FROM knowledge_note_tag WHERE note_id = :id ORDER BY name")
                .setParameter("id", noteId).getResultList();
        return names;
    }

    @Test
    @DisplayName("기존 태그 하나를 유지하고 다른 태그를 바꿔도 flush 가 성공한다")
    void replaceTags_keepsOneAndReplacesAnother_flushSucceeds() {
        KnowledgeNote note = saveNote(me, KnowledgeCategory.DEV, "메모", "출처", null, null, "spring", "jpa");
        em.flush();
        em.clear();

        KnowledgeNote loaded = repository.findById(note.getId()).orElseThrow();
        loaded.replaceTags(List.of("spring", "mysql"));
        em.flush();
        em.clear();

        assertThat(storedTagNames(note.getId())).containsExactly("mysql", "spring");
    }

    @Test
    @DisplayName("대소문자만 바꾼 태그(AI→ai)도 flush 가 성공하고 'ai' 로 저장된다")
    void replaceTags_caseOnlyChange_flushesAndStoresNewSpelling() {
        KnowledgeNote note = saveNote(me, KnowledgeCategory.DEV, "메모", "출처", null, null, "AI");
        em.flush();
        em.clear();

        KnowledgeNote loaded = repository.findById(note.getId()).orElseThrow();
        loaded.replaceTags(List.of("ai"));
        em.flush();
        em.clear();

        List<String> names = storedTagNames(note.getId());
        assertThat(names).hasSize(1);
        assertThat(names.get(0)).isEqualTo("ai");
    }

    @Test
    @DisplayName("내용 NULL + 이미지 메모도 저장·검색·종목 집계되고, 이미지 URL은 회원별로 조회된다")
    void noteWithoutContentButWithImages_isStoredSearchedCountedAndImagesListedPerMember() {
        KnowledgeNote note = saveNote(me, KnowledgeCategory.STOCK, null, null, "005930", "삼성전자", "hbm");
        note.replaceImages(List.of("https://img/1.png", "https://img/2.png"));
        KnowledgeNote otherNote = saveNote(other, KnowledgeCategory.STOCK, "남의 메모", null, null, null);
        otherNote.replaceImages(List.of("https://img/other.png"));
        em.flush();
        em.clear();

        KnowledgeNote loaded = repository.findById(note.getId()).orElseThrow();
        assertThat(loaded.getContent()).isNull();
        assertThat(loaded.getImageUrls()).containsExactly("https://img/1.png", "https://img/2.png");

        assertThat(search(me, null, null, "hbm")).extracting(KnowledgeNote::getId)
                .containsExactly(note.getId());

        assertThat(repository.countByStockSymbols(me.getId(), Set.of("005930")))
                .extracting(StockNoteCount::count).containsExactly(1L);

        assertThat(repository.findImageUrlsByMemberId(me.getId()))
                .containsExactlyInAnyOrder("https://img/1.png", "https://img/2.png");
    }

    @Test
    @DisplayName("저장된 메모의 이미지를 하나 빼고 순서를 바꾸고 새로 추가한 뒤 flush·재조회해도 순서가 유지된다")
    void replaceImages_onPersistedNote_keepsOrderAfterRemoveReorderAdd() {
        KnowledgeNote note = saveNote(me, KnowledgeCategory.ETC, "c", null, null, null);
        note.replaceImages(List.of("https://img/a.png", "https://img/b.png", "https://img/c.png"));
        em.flush();
        em.clear();

        KnowledgeNote loaded = repository.findById(note.getId()).orElseThrow();
        List<String> removed = loaded.replaceImages(
                List.of("https://img/c.png", "https://img/new.png", "https://img/a.png"));
        em.flush();
        em.clear();

        assertThat(removed).containsExactly("https://img/b.png");
        assertThat(repository.findById(note.getId()).orElseThrow().getImageUrls())
                .containsExactly("https://img/c.png", "https://img/new.png", "https://img/a.png");
    }

    @Test
    @DisplayName("visibility 컬럼 도입 전 행(DEFAULT 값)은 PUBLIC 으로 읽힌다")
    void legacyRowWithoutVisibility_readsAsPublic() {
        em.createNativeQuery("INSERT INTO knowledge_note (created_at, member_id, category, content) VALUES (NOW(6), ?, 'ETC', 'x')")
                .setParameter(1, me.getId())
                .executeUpdate();
        Long id = ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
        em.clear();

        assertThat(repository.findById(id).orElseThrow().getVisibility()).isEqualTo(KnowledgeVisibility.PUBLIC);
    }
}
