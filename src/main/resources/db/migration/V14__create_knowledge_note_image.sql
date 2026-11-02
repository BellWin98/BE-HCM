-- knowledge_note_image : 지식 메모에 첨부되는 이미지(메모당 최대 5장).
--
-- 신규 테이블 생성이므로 ALGORITHM/LOCK 지정이 필요 없다.
--
-- 설계 의도:
--   * idx_knowledge_note_image_note (note_id, position) — 메모의 이미지를 position 순으로 읽는다.
--     선두 컬럼이 note_id 이므로 note FK 인덱스 역할도 겸한다.
--   * position 에는 UNIQUE 를 걸지 않는다 — 재정렬 시 건별 UPDATE 순서에 따라 일시적으로 값이
--     겹칠 수 있어 UK 가 오히려 flush 를 깨뜨린다. 순서 무결성은 엔티티(replaceImages)가 책임진다.
--   * ON DELETE CASCADE — 회원 삭제 시 knowledge_note 를 bulk delete 하는 것만으로 이미지 행이 함께 정리된다.
--     (저장소의 파일 자체는 별도로 정리해야 한다.)
CREATE TABLE `knowledge_note_image` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `note_id` bigint NOT NULL,
  `image_url` varchar(1024) NOT NULL,
  `position` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_knowledge_note_image_note` (`note_id`, `position`),
  CONSTRAINT `fk_knowledge_note_image_note` FOREIGN KEY (`note_id`) REFERENCES `knowledge_note` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
