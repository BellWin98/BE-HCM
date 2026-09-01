-- workout_comment : 운동 인증에 달린 댓글. 대댓글 없는 평면 구조다.
--
-- 신규 테이블 생성이므로 ALGORITHM/LOCK 지정이 필요 없다.
--
-- 인덱스 설계:
--   * idx_workout_comment_record_created — 목록 조회(where workout_record_id = ? order by created_at, id)와
--     인증별 댓글 수 집계(countByRecordIds)를 함께 커버한다. 정렬까지 인덱스로 처리되어 filesort 가 없고,
--     집계는 커버링 인덱스만으로 끝난다. 선두 컬럼이 workout_record_id 이므로 FK 인덱스도 겸한다.
--   * idx_workout_comment_member — member FK 용. 회원 탈퇴 정리(deleteAllByMemberOrRecordOwner)가 탄다.
--
-- content 길이는 엔티티의 WorkoutComment.MAX_CONTENT_LENGTH(500) 와 일치시킨다.
CREATE TABLE `workout_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `content` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `member_id` bigint NOT NULL,
  `workout_record_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_workout_comment_record_created` (`workout_record_id`,`created_at`,`id`),
  KEY `idx_workout_comment_member` (`member_id`),
  CONSTRAINT `fk_workout_comment_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `fk_workout_comment_record` FOREIGN KEY (`workout_record_id`) REFERENCES `workout_record` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
