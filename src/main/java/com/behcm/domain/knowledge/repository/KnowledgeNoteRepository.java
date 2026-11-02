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

public interface KnowledgeNoteRepository
        extends JpaRepository<KnowledgeNote, Long>, JpaSpecificationExecutor<KnowledgeNote> {

    Optional<KnowledgeNote> findByIdAndMemberId(Long id, Long memberId);

    /** 종목별 내 메모 건수. 종목 목록 화면에서 건별 count 를 돌리면 N+1 이 되므로 한 번에 집계한다. */
    @Query("SELECT new com.behcm.domain.knowledge.repository.StockNoteCount(n.stockSymbol, COUNT(n)) "
            + "FROM KnowledgeNote n "
            + "WHERE n.member.id = :memberId AND n.stockSymbol IN :symbols "
            + "GROUP BY n.stockSymbol")
    List<StockNoteCount> countByStockSymbols(@Param("memberId") Long memberId,
                                             @Param("symbols") Collection<String> symbols);

    /** 회원 삭제용 bulk delete. 태그는 FK ON DELETE CASCADE 로 함께 정리된다. */
    @Modifying
    @Query("DELETE FROM KnowledgeNote n WHERE n.member.id = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);
}
