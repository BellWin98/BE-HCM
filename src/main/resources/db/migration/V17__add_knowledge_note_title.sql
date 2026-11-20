-- knowledge_note.title : 메모 제목(선택). 목록에서 메모를 알아보기 위한 한 줄.
-- 기존 메모는 NULL 로 둔다 — 목록은 제목이 없으면 내용 첫 줄을 대신 보여준다.
ALTER TABLE `knowledge_note` ADD COLUMN `title` varchar(100) NULL, ALGORITHM = INPLACE, LOCK = NONE;
