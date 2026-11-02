-- knowledge_note.content 를 NULL 허용으로 완화한다.
--
-- 배경:
--   지식 메모에 이미지를 첨부할 수 있게 되면서, 글 없이 이미지만으로 된 메모를 저장할 수 있어야 한다.
--   (내용/이미지 중 하나 이상 있어야 한다는 검증은 서비스 책임이다.)
--   KnowledgeNote 엔티티의 content 도 nullable 로 바뀐다.
--
-- 기존 행은 모두 값이 있으므로 데이터 변경은 없다. NOT NULL -> NULL 완화는 테이블 재구성 없이
-- 메타데이터만 바꾸는 INPLACE 로 처리된다.
ALTER TABLE `knowledge_note`
    MODIFY COLUMN `content` TEXT NULL,
    ALGORITHM = INPLACE, LOCK = NONE;
