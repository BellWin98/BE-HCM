package com.behcm.domain.knowledge.service;

import com.behcm.domain.knowledge.entity.KnowledgeNote;

/**
 * 지식 메모 생성 푸시 문구. 본문 길이 제한(말줄임)은 발송 단계가 맡으므로 여기서 자르지 않는다.
 * 헤드라인 규칙은 FE noteHeadline.ts 와 같다: 제목 → 본문 첫 줄(코드 펜스 줄 제외) → "이미지 메모".
 */
final class KnowledgeNoteNotification {

    static final String TYPE = "KNOWLEDGE_NOTE_CREATED";
    static final String PATH = "/notes";
    private static final String IMAGE_ONLY_HEADLINE = "이미지 메모";
    private static final String CODE_FENCE = "```";

    private KnowledgeNoteNotification() {
    }

    static String title(String nickname) {
        return "📝 " + nickname + "님이 새 메모를 올렸어요!";
    }

    static String body(KnowledgeNote note) {
        String headline = headline(note);
        String stockName = note.getStockName();
        if (stockName == null || stockName.isBlank()) {
            return headline;
        }
        return "[" + stockName.strip() + "] " + headline;
    }

    private static String headline(KnowledgeNote note) {
        String title = note.getTitle();
        if (title != null && !title.isBlank()) {
            return title.strip();
        }
        String content = note.getContent();
        if (content != null) {
            for (String line : content.lines().toList()) {
                String stripped = line.strip();
                if (!stripped.isEmpty() && !stripped.startsWith(CODE_FENCE)) {
                    return stripped;
                }
            }
        }
        return IMAGE_ONLY_HEADLINE;
    }
}
