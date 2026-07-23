-- Flyway 도입 시점의 기준 스키마(baseline).
--
-- 이 파일은 Flyway 도입 이전에 hibernate ddl-auto=update가 만들어 온 스키마를 그대로 옮긴 것이다.
-- 기존 dev/prod DB에는 이미 이 스키마가 존재하므로 baseline-on-migrate 설정에 의해 실행되지 않고,
-- 신규 DB(로컬/테스트/신규 환경)에서만 실행된다. 따라서 이 파일은 수정하지 말 것 —
-- 스키마 변경은 항상 새로운 V___ 마이그레이션으로 추가한다.
--
-- 제약조건/인덱스 이름(FK..., UK...)은 Hibernate가 테이블+컬럼명 해시로 생성한 값이며
-- 기존 DB에 실재하는 이름과 동일하다. 이름을 바꾸면 환경 간 스키마가 어긋나므로 유지한다.

-- 테이블 생성 순서와 무관하게 FK를 걸 수 있도록 잠시 해제한다.
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text COLLATE utf8mb4_unicode_ci,
  `image_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `message_type` enum('IMAGE','TEXT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `timestamp` datetime(6) DEFAULT NULL,
  `sender` bigint NOT NULL,
  `workout_room` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKf6j5bc1aeullkew3c3utkxh02` (`sender`),
  KEY `FKb5jqp9m9s2pl26qv52207qfj2` (`workout_room`),
  CONSTRAINT `FKb5jqp9m9s2pl26qv52207qfj2` FOREIGN KEY (`workout_room`) REFERENCES `workout_room` (`id`),
  CONSTRAINT `FKf6j5bc1aeullkew3c3utkxh02` FOREIGN KEY (`sender`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `email_verification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `is_verified` bit(1) NOT NULL,
  `verification_code` varchar(6) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `fcm_token` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `token` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKd83f58imj8mrd7j9ddrg9d7pd` (`token`),
  KEY `idx_member_id` (`member_id`),
  CONSTRAINT `FKf1rbjf8lle4r2in6ovkcgl0w8` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `bio` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nickname` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `oauth_provider` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `oauth_provider_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `profile_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role` enum('ADMIN','FAMILY','USER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `total_penalty` bigint NOT NULL,
  `total_workout_days` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKmbmcqelty0fbrvxp1q58dn57t` (`email`),
  UNIQUE KEY `UKhh9kg6jti4n1eoiertn2k6qsc` (`nickname`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `member_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `penalty_alert` bit(1) NOT NULL,
  `room_updates` bit(1) NOT NULL,
  `show_profile` bit(1) NOT NULL,
  `show_stats` bit(1) NOT NULL,
  `show_workouts` bit(1) NOT NULL,
  `weekly_report` bit(1) NOT NULL,
  `workout_reminder` bit(1) NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKsrkd50tt2y05a5uejjoije46b` (`member_id`),
  CONSTRAINT `FKcqs07i6oa23abo947u2lua993` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `penalty` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `actual_workouts` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `is_paid` bit(1) NOT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `penalty_amount` bigint NOT NULL,
  `required_workouts` int NOT NULL,
  `week_end_date` date NOT NULL,
  `week_start_date` date NOT NULL,
  `workout_room_member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9bu35jejlepkvcurshay91m0e` (`workout_room_member_id`),
  CONSTRAINT `FK9bu35jejlepkvcurshay91m0e` FOREIGN KEY (`workout_room_member_id`) REFERENCES `workout_room_member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `penalty_account` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `account_holder` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `account_number` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `bank_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `workout_room_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKhwcfkpvbonswuclhqup784984` (`workout_room_id`),
  CONSTRAINT `FKhwcfkpvbonswuclhqup784984` FOREIGN KEY (`workout_room_id`) REFERENCES `workout_room` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `rest` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `end_date` date NOT NULL,
  `reason` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `start_date` date NOT NULL,
  `workout_room_member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKhac7wxqkpe51qh8fsrh2qqvj1` (`workout_room_member_id`),
  CONSTRAINT `FKhac7wxqkpe51qh8fsrh2qqvj1` FOREIGN KEY (`workout_room_member_id`) REFERENCES `workout_room_member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `workout_image` (
  `workout_record_id` bigint NOT NULL,
  `image_url` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  KEY `FK17ntf7lxrsvjlparvw3lugdqo` (`workout_record_id`),
  CONSTRAINT `FK17ntf7lxrsvjlparvw3lugdqo` FOREIGN KEY (`workout_record_id`) REFERENCES `workout_record` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `workout_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `duration` int NOT NULL,
  `workout_date` date NOT NULL,
  `member_id` bigint NOT NULL,
  `workout_room_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workout_record_member_room_date` (`member_id`,`workout_room_id`,`workout_date`),
  KEY `FKr2x0c7dka1sffdk8gq86qrnvc` (`workout_room_id`),
  CONSTRAINT `FK74xs5e7kdb51incmxmardw2b7` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `FKr2x0c7dka1sffdk8gq86qrnvc` FOREIGN KEY (`workout_room_id`) REFERENCES `workout_room` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `workout_room` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `entry_code` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_active` bit(1) NOT NULL,
  `max_members` int NOT NULL,
  `min_weekly_workouts` int NOT NULL,
  `name` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `penalty_change_effective_date` date DEFAULT NULL,
  `penalty_enabled` tinyint(1) NOT NULL DEFAULT '1',
  `penalty_per_miss` bigint DEFAULT NULL,
  `pending_penalty_enabled` bit(1) DEFAULT NULL,
  `pending_penalty_per_miss` bigint DEFAULT NULL,
  `owner_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKcta59tkijkwcoy0g68iqrhc7u` (`entry_code`),
  KEY `FKc0sm0oag2x42so0i3mvne16nv` (`owner_id`),
  CONSTRAINT `FKc0sm0oag2x42so0i3mvne16nv` FOREIGN KEY (`owner_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `workout_room_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `is_on_break` bit(1) NOT NULL,
  `joined_at` datetime(6) NOT NULL,
  `total_penalty` bigint NOT NULL,
  `total_workouts` int NOT NULL,
  `weekly_workouts` int NOT NULL,
  `last_read_chat_message_id` bigint DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `workout_room_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4mc9v6t4e0p467fliwp3vg1ia` (`member_id`,`workout_room_id`),
  KEY `FK7w12mn3v41ukstpcm50ux904m` (`last_read_chat_message_id`),
  KEY `FKfbi1tcm0iwexpey638yrq9t95` (`workout_room_id`),
  CONSTRAINT `FK18my12kbg0pidatoyskv5r616` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `FK7w12mn3v41ukstpcm50ux904m` FOREIGN KEY (`last_read_chat_message_id`) REFERENCES `chat_message` (`id`),
  CONSTRAINT `FKfbi1tcm0iwexpey638yrq9t95` FOREIGN KEY (`workout_room_id`) REFERENCES `workout_room` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `workout_type` (
  `workout_record_id` bigint NOT NULL,
  `workout_type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  KEY `FK2jav763fafrr7yfw9381n3wwb` (`workout_record_id`),
  CONSTRAINT `FK2jav763fafrr7yfw9381n3wwb` FOREIGN KEY (`workout_record_id`) REFERENCES `workout_record` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
