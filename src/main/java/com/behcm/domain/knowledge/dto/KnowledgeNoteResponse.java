package com.behcm.domain.knowledge.dto;

import com.behcm.domain.knowledge.entity.KnowledgeCategory;
import com.behcm.domain.knowledge.entity.KnowledgeNote;
import com.behcm.domain.knowledge.entity.KnowledgeVisibility;
import com.behcm.domain.knowledge.service.KnowledgePermissions;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @param editable 요청자가 이 메모를 수정·삭제할 수 있는지({@link KnowledgePermissions#canEdit}).
 */
public record KnowledgeNoteResponse(
        Long id,
        KnowledgeCategory category,
        String content,
        String sourceUrl,
        String sourceText,
        List<String> tags,
        String stockSymbol,
        String stockName,
        List<String> images,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        KnowledgeVisibility visibility,
        KnowledgeNoteAuthor author,
        boolean editable
) {

    public static KnowledgeNoteResponse of(KnowledgeNote note, Long viewerId, KnowledgePermissions p) {
        return new KnowledgeNoteResponse(
                note.getId(),
                note.getCategory(),
                note.getContent(),
                note.getSourceUrl(),
                note.getSourceText(),
                note.getTagNames(),
                note.getStockSymbol(),
                note.getStockName(),
                note.getImageUrls(),
                note.getCreatedAt(),
                note.getUpdatedAt(),
                note.getVisibility(),
                KnowledgeNoteAuthor.from(note.getMember()),
                p.canEdit(note, viewerId)
        );
    }
}
