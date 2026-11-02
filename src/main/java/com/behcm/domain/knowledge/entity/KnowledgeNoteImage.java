package com.behcm.domain.knowledge.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "knowledge_note_image",
        indexes = {
                @Index(name = "idx_knowledge_note_image_note", columnList = "note_id, position")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnowledgeNoteImage {

    public static final int MAX_IMAGE_URL_LENGTH = 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id", nullable = false)
    private KnowledgeNote note;

    @Column(nullable = false, length = MAX_IMAGE_URL_LENGTH)
    private String imageUrl;

    @Column(nullable = false)
    private int position;

    public KnowledgeNoteImage(KnowledgeNote note, String imageUrl, int position) {
        this.note = note;
        this.imageUrl = imageUrl;
        this.position = position;
    }

    public void changePosition(int position) {
        this.position = position;
    }
}
