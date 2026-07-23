-- workout_room.penalty_per_miss 를 NULL 허용으로 완화한다.
--
-- 배경:
--   WorkoutRoom 엔티티의 penaltyPerMiss 는 @Column(nullable 기본값 = true) 이고,
--   WorkoutRoomService 는 벌금 기능이 꺼진 방을 만들 때 이 값에 명시적으로 null 을 넣는다.
--       .penaltyPerMiss(request.getPenaltyEnabled() ? request.getPenaltyPerMiss() : null)
--
--   그러나 penalty_enabled 기능이 추가되기 전에 만들어진 DB 에서는 이 컬럼이 NOT NULL 로 남아 있다.
--   hibernate ddl-auto=update 는 컬럼을 새로 추가하기만 할 뿐, 기존 컬럼의 nullability 를 절대
--   완화하지 않기 때문이다. 그 결과 벌금 비활성화 방 생성이 런타임에 제약 위반으로 실패한다.
--
-- 신규 DB 에서는 V1 이 이미 NULL 허용으로 만들었으므로 이 문장은 사실상 no-op 이다.
ALTER TABLE `workout_room`
    MODIFY COLUMN `penalty_per_miss` BIGINT NULL,
    ALGORITHM = INPLACE, LOCK = NONE;
