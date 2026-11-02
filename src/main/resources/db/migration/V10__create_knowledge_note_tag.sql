-- knowledge_note_tag : 지식 메모에 붙는 태그.
--
-- 신규 테이블 생성이므로 ALGORITHM/LOCK 지정이 필요 없다.
--
-- 설계 의도:
--   * uk_knowledge_note_tag_note_name — 한 메모에 같은 태그가 중복 저장되지 않도록 DB 레벨에서 보장한다.
--     선두 컬럼이 note_id 이므로 note FK 인덱스 역할도 겸한다.
--   * ON DELETE CASCADE — 회원 삭제 시 knowledge_note 를 bulk delete 하는 것만으로 태그가 함께 정리된다.
--     (JPA 가 태그를 건별로 지우는 경로를 만들 필요가 없다.)
CREATE TABLE `knowledge_note_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `note_id` bigint NOT NULL,
  `name` varchar(30) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_knowledge_note_tag_note_name` (`note_id`, `name`),
  CONSTRAINT `fk_knowledge_note_tag_note` FOREIGN KEY (`note_id`) REFERENCES `knowledge_note` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
