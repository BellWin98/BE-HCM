-- knowledge_note.visibility : 메모 공개 범위(PUBLIC / PRIVATE).
--
-- 기존 메모를 PUBLIC 으로 두는 것은 의도적이다. 배포 직후 조회 권한자가 관리자 메모를 바로 볼 수 있어야 한다.
-- 비공개로 돌리는 것은 작성자가 개별 메모에서 직접 지정한다.
--
-- ADD COLUMN ... NOT NULL DEFAULT 는 INPLACE/LOCK=NONE 으로 수행되어 읽기·쓰기를 막지 않는다.
ALTER TABLE `knowledge_note` ADD COLUMN `visibility` varchar(10) NOT NULL DEFAULT 'PUBLIC', ALGORITHM = INPLACE, LOCK = NONE;
