-- knowledge_note : 관리자 전용 지식 메모.
--
-- 신규 테이블 생성이므로 ALGORITHM/LOCK 지정이 필요 없다(기존 데이터에 대한 잠금이 발생하지 않는다).
--
-- 설계 의도:
--   메모의 소유 범위는 "작성한 회원 본인"이다. 별도의 owner 개념(공유/팀 소유)은 두지 않으며,
--   모든 조회·수정·삭제는 member_id 로 한정해서 다른 회원의 메모에 접근할 수 없게 한다.
--   접근 자체는 ADMIN 에게만 열려 있다(컨트롤러/서비스 계층에서 보장).
--
-- 인덱스 설계:
--   * idx_knowledge_note_member_created — 내 메모 최신순 목록(member_id 필터 + created_at 정렬).
--   * idx_knowledge_note_member_stock   — 내 메모의 종목별 필터/건수 집계.
--   member_id 가 두 인덱스의 선두 컬럼이므로 member FK 인덱스 역할도 겸한다.
CREATE TABLE `knowledge_note` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `category` varchar(20) NOT NULL,
  `content` text NOT NULL,
  `source_url` varchar(2048) DEFAULT NULL,
  `source_text` varchar(500) DEFAULT NULL,
  `stock_symbol` varchar(20) DEFAULT NULL,
  `stock_name` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_knowledge_note_member_created` (`member_id`, `created_at`),
  KEY `idx_knowledge_note_member_stock` (`member_id`, `stock_symbol`),
  CONSTRAINT `fk_knowledge_note_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
