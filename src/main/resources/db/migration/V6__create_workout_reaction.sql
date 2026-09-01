-- workout_reaction : 운동 인증에 대한 이모지 리액션.
--
-- 신규 테이블 생성이므로 ALGORITHM/LOCK 지정이 필요 없다(기존 데이터에 대한 잠금이 발생하지 않는다).
--
-- 인덱스 설계:
--   * uk_workout_reaction_record_member — "한 회원은 인증 하나에 이모지 하나"를 DB 레벨에서 보장한다.
--     동시 요청으로 같은 회원의 리액션이 두 건 생기는 것을 막는 것이 주 목적이고,
--     선두 컬럼이 workout_record_id 이므로 인증별 집계 쿼리
--     (countByRecordIdsGroupByEmoji)와 workout_record FK 인덱스 역할까지 겸한다.
--   * idx_workout_reaction_member — member FK 용. 회원 탈퇴 정리(deleteAllByMemberOrRecordOwner)와
--     "내가 누른 리액션" 조회(findEmojiByRecordIdsAndMember)가 이 인덱스를 탄다.
--
-- emoji 는 이모지 문자가 아니라 ReactionEmoji 의 이름(MUSCLE 등)을 저장한다.
-- 표기를 바꿔야 할 때 기존 데이터를 건드리지 않기 위함이다.
CREATE TABLE `workout_reaction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `emoji` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `member_id` bigint NOT NULL,
  `workout_record_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workout_reaction_record_member` (`workout_record_id`,`member_id`),
  KEY `idx_workout_reaction_member` (`member_id`),
  CONSTRAINT `fk_workout_reaction_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_workout_reaction_record` FOREIGN KEY (`workout_record_id`) REFERENCES `workout_record` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
