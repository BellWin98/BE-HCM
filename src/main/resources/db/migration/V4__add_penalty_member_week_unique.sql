-- penalty : 멤버당 주차별 벌금은 1건이어야 한다.
--
-- 성능 목적(findAllByWorkoutRoomIdAndWeekOverlapping)보다 정합성 목적이 크다.
-- 주간 정산 스케줄러(WorkoutSchedulingService)가 어떤 이유로든 두 번 실행되면
-- 현재는 같은 주차 벌금이 중복 생성된다. 이 제약이 그것을 DB 레벨에서 차단한다.
--
-- ############################################################################
-- # 배포 전 필수 점검 — 중복이 있으면 이 마이그레이션은 실패하고 앱이 부팅되지 않는다.
-- #   SELECT workout_room_member_id, week_start_date, COUNT(*) AS cnt
-- #   FROM penalty
-- #   GROUP BY workout_room_member_id, week_start_date
-- #   HAVING cnt > 1;
-- # 결과가 0건이 아니면 데이터를 먼저 정리할 것.
-- ############################################################################
--
-- 주의: 이 UNIQUE 를 만들면 기존 DB 에서는 FK 전용 인덱스(workout_room_member_id 단일 컬럼)가
-- 중복이 되어 MySQL 이 자동으로 제거한다. InnoDB 가 FK 제약을 위해 스스로 만든 인덱스이기 때문이다.
-- 따라서 이를 DROP INDEX 로 지우는 마이그레이션을 두면 "그런 인덱스 없음"으로 실패한다.
-- (V1 baseline 으로 만들어진 신규 DB 에서는 해당 인덱스가 명시적으로 생성되므로 그대로 남는다.
--  환경 간 차이가 생기지만 중복 인덱스가 남는 것뿐이라 무해하다.)
--
-- 또한 파일 하나에 DDL 한 개만 둔다. MySQL 은 DDL 을 롤백하지 않으므로 여러 DDL 을 묶으면
-- 중간 실패 시 부분 적용 상태가 남아 재실행이 불가능해진다.
CREATE UNIQUE INDEX `uk_penalty_member_week`
    ON `penalty` (`workout_room_member_id`, `week_start_date`)
    ALGORITHM = INPLACE LOCK = NONE;
