package com.behcm.domain.knowledge.dto;

import com.behcm.domain.knowledge.entity.KnowledgeCategory;
import com.behcm.domain.knowledge.entity.KnowledgeNote;

import java.time.LocalDateTime;
import java.util.List;

public record KnowledgeNoteResponse(
        Long id,
        KnowledgeCategory category,
        String content,
        String sourceUrl,
        String sourceText,
        List<String> tags,
        String stockSymbol,
        String stockName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static KnowledgeNoteResponse from(KnowledgeNote note) {
        return new KnowledgeNoteResponse(
                note.getId(),
                note.getCategory(),
                note.getContent(),
                note.getSourceUrl(),
                note.getSourceText(),
                note.getTagNames(),
                note.getStockSymbol(),
                note.getStockName(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }
}
