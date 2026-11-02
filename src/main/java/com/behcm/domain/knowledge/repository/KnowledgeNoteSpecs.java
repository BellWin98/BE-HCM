package com.behcm.domain.knowledge.repository;

import com.behcm.domain.knowledge.entity.KnowledgeCategory;
import com.behcm.domain.knowledge.entity.KnowledgeNote;
import com.behcm.domain.knowledge.entity.KnowledgeNoteTag;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class KnowledgeNoteSpecs {

    private static final char ESCAPE = '\\';

    private KnowledgeNoteSpecs() {
    }

    /**
     * 내 메모 검색. 모든 term 이 (내용/출처/종목/태그) 중 어딘가에 포함되어야 한다(AND).
     * 태그는 조인하지 않고 EXISTS 서브쿼리로 비교한다 — 조인하면 태그 수만큼 행이 늘어 페이지가 중복된다.
     */
    public static Specification<KnowledgeNote> search(Long memberId, KnowledgeCategory category,
                                                      String stockSymbol, List<String> terms) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("member").get("id"), memberId));
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (stockSymbol != null) {
                predicates.add(cb.equal(root.get("stockSymbol"), stockSymbol));
            }
            if (terms != null) {
                for (String term : terms) {
                    predicates.add(matchesTerm(root, query, cb, "%" + escapeLike(term) + "%"));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate matchesTerm(Root<KnowledgeNote> root,
                                         jakarta.persistence.criteria.CriteriaQuery<?> query,
                                         CriteriaBuilder cb, String pattern) {
        Subquery<Long> tagMatch = query.subquery(Long.class);
        Root<KnowledgeNoteTag> tag = tagMatch.from(KnowledgeNoteTag.class);
        tagMatch.select(tag.get("id"))
                .where(cb.equal(tag.get("note"), root), cb.like(tag.get("name"), pattern, ESCAPE));

        return cb.or(
                cb.like(root.get("content"), pattern, ESCAPE),
                cb.like(root.get("sourceText"), pattern, ESCAPE),
                cb.like(root.get("sourceUrl"), pattern, ESCAPE),
                cb.like(root.get("stockName"), pattern, ESCAPE),
                cb.like(root.get("stockSymbol"), pattern, ESCAPE),
                cb.exists(tagMatch)
        );
    }

    private static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
