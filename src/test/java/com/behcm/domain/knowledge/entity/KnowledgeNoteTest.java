package com.behcm.domain.knowledge.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeNoteTest {

    private KnowledgeNote noteWithImages(String... urls) {
        KnowledgeNote note = KnowledgeNote.builder().category(KnowledgeCategory.ETC).content("c").build();
        note.replaceImages(List.of(urls));
        return note;
    }

    @Test
    void replaceImages_keepsOrderAndReturnsRemoved() {
        KnowledgeNote note = noteWithImages("a", "b", "c");

        List<String> removed = note.replaceImages(List.of("a", "c", "d"));

        assertThat(removed).containsExactly("b");
        assertThat(note.getImageUrls()).containsExactly("a", "c", "d");
    }

    @Test
    void replaceImages_reorderOnlyUpdatesPosition() {
        KnowledgeNote note = noteWithImages("a", "b");

        List<String> removed = note.replaceImages(List.of("b", "a"));

        assertThat(removed).isEmpty();
        assertThat(note.getImageUrls()).containsExactly("b", "a");
    }
}
