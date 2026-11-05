package com.behcm.domain.knowledge.repository;

import com.behcm.domain.knowledge.entity.KnowledgeCategory;
import com.behcm.domain.knowledge.entity.KnowledgeNote;
import com.behcm.domain.knowledge.entity.KnowledgeNoteTag;
import com.behcm.domain.knowledge.entity.KnowledgeVisibility;
import com.behcm.domain.knowledge.service.KnowledgePermissions;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class KnowledgeNoteSpecs {

    private static final char ESCAPE = '\\';

    private KnowledgeNoteSpecs() {
    }

    /**
     * 요청자에게 보이는 메모. 다른 회원의 비공개 메모는 ADMIN 이 아니면 어떤 scope 로도 나오지 않는다.
     *
     * <pre>
     * base   = admin ? (조건 없음) : (canRead ? mine OR public : mine)
     * picked = scopes 비어 있음 ? (admin ? mine OR public : 조건 없음) : 고른 scope 들의 OR
     * result = base AND picked
     * </pre>
     */
    public static Specification<KnowledgeNote> visibleTo(Long viewerId, KnowledgePermissions p,
                                                         Set<KnowledgeNoteScope> scopes) {
        return (root, query, cb) -> {
            Predicate mine = cb.equal(root.get("member").get("id"), viewerId);
            Predicate isPublic = cb.equal(root.get("visibility"), KnowledgeVisibility.PUBLIC);
            Predicate isPrivate = cb.equal(root.get("visibility"), KnowledgeVisibility.PRIVATE);

            List<Predicate> conditions = new ArrayList<>();
            if (!p.admin()) {
                conditions.add(p.canRead() ? cb.or(mine, isPublic) : mine);
            }
            if (scopes == null || scopes.isEmpty()) {
                if (p.admin()) {
                    conditions.add(cb.or(mine, isPublic));
                }
            } else {
                List<Predicate> picked = new ArrayList<>();
                for (KnowledgeNoteScope scope : scopes) {
                    picked.add(switch (scope) {
                        case MINE -> mine;
                        case PUBLIC -> isPublic;
                        case PRIVATE -> isPrivate;
                    });
                }
                conditions.add(cb.or(picked.toArray(new Predicate[0])));
            }
            return cb.and(conditions.toArray(new Predicate[0]));
        };
    }

    /**
     * 검색 조건. 모든 term 이 (내용/출처/종목/태그) 중 어딘가에 포함되어야 한다(AND).
     * 노출 범위는 걸지 않으므로 반드시 {@link #visibleTo} 와 함께 쓴다.
     * 태그는 조인하지 않고 EXISTS 서브쿼리로 비교한다 — 조인하면 태그 수만큼 행이 늘어 페이지가 중복된다.
     */
    public static Specification<KnowledgeNote> matches(KnowledgeCategory category, String stockSymbol,
                                                       List<String> terms) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
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
