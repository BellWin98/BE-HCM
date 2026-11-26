package com.behcm.domain.knowledge.service;

import com.behcm.domain.knowledge.entity.KnowledgeCategory;
import com.behcm.domain.knowledge.entity.KnowledgeNote;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeNoteNotificationTest {

    private static KnowledgeNote note(String title, String content, String stockName) {
        Member m = Member.builder().email("a@b.c").password("p").nickname("n").role(MemberRole.FAMILY).build();
        return KnowledgeNote.builder().member(m).category(KnowledgeCategory.DEV)
                .title(title).content(content).stockName(stockName).build();
    }

    @Test
    @DisplayName("제목과 종목이 있으면 [종목] 제목")
    void body_titleAndStock() {
        assertThat(KnowledgeNoteNotification.body(note("실적 메모", "내용", "삼성전자")))
                .isEqualTo("[삼성전자] 실적 메모");
    }

    @Test
    @DisplayName("제목이 없으면 코드 펜스를 건너뛴 본문 첫 줄")
    void body_noTitle_usesFirstContentLineSkippingFences() {
        assertThat(KnowledgeNoteNotification.body(note(null, "\n```sql\nSELECT 1\n```", null)))
                .isEqualTo("SELECT 1");
    }

    @Test
    @DisplayName("제목·본문이 없으면 이미지 메모")
    void body_imageOnly() {
        assertThat(KnowledgeNoteNotification.body(note(null, null, null))).isEqualTo("이미지 메모");
    }

    @Test
    @DisplayName("제목 문구 형식")
    void title_format() {
        assertThat(KnowledgeNoteNotification.title("철수")).isEqualTo("📝 철수님이 새 메모를 올렸어요!");
    }

    @Test
    @DisplayName("공백 제목은 본문으로 대체하고 공백 종목은 접두사를 붙이지 않는다")
    void body_blankTitleAndStock() {
        assertThat(KnowledgeNoteNotification.body(note("  ", "  첫 줄  \n둘째", " "))).isEqualTo("첫 줄");
    }
}
