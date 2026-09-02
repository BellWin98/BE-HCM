-- toss_access : 토스증권 화면/API 에 대한 회원별 접근 권한.
--
-- 신규 테이블 생성이므로 ALGORITHM/LOCK 지정이 필요 없다(기존 데이터에 대한 잠금이 발생하지 않는다).
--
-- 설계 의도:
--   토스 접근을 member.role 에서 분리한다. FAMILY 는 한국투자증권(/api/stock)까지 함께 여는 역할이고
--   role 은 단일 값 컬럼이라 "특정 회원에게 토스만 허용"을 표현할 수 없었다.
--   ADMIN 은 이 테이블에 행이 없어도 항상 접근하며(TossAccessChecker), 그 외 회원은 여기 등록된 경우만 접근한다.
--
-- 기존 FAMILY 회원을 시드하지 않는 것은 의도적이다. 배포 직후에는 ADMIN 만 접근할 수 있고,
-- 관리자 페이지(/admin/members 의 "토스 접근" 토글)에서 사람이 직접 지정해야 한다.
--
-- 인덱스 설계:
--   * uk_toss_access_member — 한 회원당 한 행을 DB 레벨에서 보장한다(중복 부여 방지).
--     선두 컬럼이 member_id 이므로 존재 여부 조회와 member FK 인덱스 역할까지 겸한다.
--
-- granted_by 는 부여한 관리자의 id 를 FK 없이 보관한다. FK 를 걸면 그 관리자를 삭제할 때
-- AdminMemberService.deleteMember 에 정리 경로가 하나 더 늘어난다 — 감사 흔적일 뿐이라 무결성을 요구하지 않는다.
CREATE TABLE `toss_access` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `granted_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_toss_access_member` (`member_id`),
  CONSTRAINT `fk_toss_access_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
