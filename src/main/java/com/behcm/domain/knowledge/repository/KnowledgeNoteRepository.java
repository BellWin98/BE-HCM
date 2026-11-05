package com.behcm.domain.knowledge.repository;

import com.behcm.domain.knowledge.entity.KnowledgeNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface KnowledgeNoteRepository
        extends JpaRepository<KnowledgeNote, Long>, JpaSpecificationExecutor<KnowledgeNote> {

    /**
     * 요청자에게 보이는 메모 단건. 본인 메모이거나, includePublic 이면 공개 메모까지.
     * 다른 회원의 비공개 메모는 어떤 경우에도 나오지 않는다.
     */
    @Query("SELECT n FROM KnowledgeNote n WHERE n.id = :id AND (n.member.id = :viewerId "
            + "OR (:includePublic = true AND n.visibility = com.behcm.domain.knowledge.entity.KnowledgeVisibility.PUBLIC))")
    Optional<KnowledgeNote> findVisibleById(@Param("id") Long id,
                                            @Param("viewerId") Long viewerId,
                                            @Param("includePublic") boolean includePublic);

    /**
     * 종목별로 요청자에게 보이는 메모 건수({@link #findVisibleById} 와 같은 범위).
     * 종목 목록 화면에서 건별 count 를 돌리면 N+1 이 되므로 한 번에 집계한다.
     */
    @Query("SELECT new com.behcm.domain.knowledge.repository.StockNoteCount(n.stockSymbol, COUNT(n)) "
            + "FROM KnowledgeNote n "
            + "WHERE (n.member.id = :viewerId "
            + "OR (:includePublic = true AND n.visibility = com.behcm.domain.knowledge.entity.KnowledgeVisibility.PUBLIC)) "
            + "AND n.stockSymbol IN :symbols "
            + "GROUP BY n.stockSymbol")
    List<StockNoteCount> countVisibleByStockSymbols(@Param("viewerId") Long viewerId,
                                                    @Param("includePublic") boolean includePublic,
                                                    @Param("symbols") Collection<String> symbols);

    /** 회원 삭제용 bulk delete. 태그는 FK ON DELETE CASCADE 로 함께 정리된다. */
    @Modifying
    @Query("DELETE FROM KnowledgeNote n WHERE n.member.id = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);

    /** 회원의 모든 메모에 붙은 이미지 URL. 회원 삭제 시 저장소 파일 정리에 쓴다. */
    @Query("SELECT i.imageUrl FROM KnowledgeNoteImage i WHERE i.note.member.id = :memberId")
    List<String> findImageUrlsByMemberId(@Param("memberId") Long memberId);

    /**
     * urls 중 noteId 가 아닌 다른 메모에 붙어 있는 것. 이미지 재사용 차단과 공유 파일 보호에 쓴다.
     * 새 메모에는 id 가 없으므로 noteId 로 -1L 을 넘긴다. urls 가 비면 호출하지 않는다(IN () 회피).
     */
    @Query("SELECT DISTINCT i.imageUrl FROM KnowledgeNoteImage i WHERE i.imageUrl IN :urls AND i.note.id <> :noteId")
    Set<String> findUrlsUsedByOtherNotes(@Param("urls") Collection<String> urls, @Param("noteId") Long noteId);

    /** urls 중 memberId 가 아닌 다른 회원의 메모에 붙어 있는 것. 회원 삭제 시 공유 파일 보호에 쓴다. */
    @Query("SELECT DISTINCT i.imageUrl FROM KnowledgeNoteImage i WHERE i.imageUrl IN :urls AND i.note.member.id <> :memberId")
    Set<String> findUrlsUsedByOtherMembers(@Param("urls") Collection<String> urls, @Param("memberId") Long memberId);
}
