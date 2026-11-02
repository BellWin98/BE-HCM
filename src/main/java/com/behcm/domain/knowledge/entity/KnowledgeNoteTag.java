package com.behcm.domain.knowledge.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "knowledge_note_tag",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_knowledge_note_tag_note_name",
                        columnNames = {"note_id", "name"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnowledgeNoteTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id", nullable = false)
    private KnowledgeNote note;

    @Column(nullable = false, length = KnowledgeNote.MAX_TAG_LENGTH)
    private String name;

    public KnowledgeNoteTag(KnowledgeNote note, String name) {
        this.note = note;
        this.name = name;
    }

    public void rename(String name) {
        this.name = name;
    }
}
