-- penalty_account : 방당 계좌는 1건이어야 한다.
--
-- PenaltyAccountRepository.findByWorkoutRoom 이 Optional 을 반환하므로 코드는 이미 1:1 을
-- 전제하고 있으나, DB 에는 제약이 없어 중복 삽입을 막지 못한다.
--
-- ############################################################################
-- # 배포 전 필수 점검 — 중복이 있으면 이 마이그레이션은 실패하고 앱이 부팅되지 않는다.
-- #   SELECT workout_room_id, COUNT(*) AS cnt
-- #   FROM penalty_account
-- #   GROUP BY workout_room_id
-- #   HAVING cnt > 1;
-- # 결과가 0건이 아니면 데이터를 먼저 정리할 것.
-- ############################################################################
CREATE UNIQUE INDEX `uk_penalty_account_room`
    ON `penalty_account` (`workout_room_id`)
    ALGORITHM = INPLACE LOCK = NONE;
