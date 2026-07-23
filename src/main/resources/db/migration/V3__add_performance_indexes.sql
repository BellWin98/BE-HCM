-- 조회 성능을 위한 인덱스 추가.
--
-- 모두 세컨더리 인덱스 추가이므로 MySQL 8 InnoDB 의 온라인 DDL(INPLACE / LOCK=NONE)로 처리된다.
-- ALGORITHM/LOCK 을 명시해 두면, 어떤 이유로든 온라인으로 처리할 수 없을 때 조용히 테이블을
-- 잠그는 대신 즉시 에러로 실패한다.
--
-- 아래에 없는 것들은 의도적으로 제외했다:
--   * chat_message : 커서 페이징(where workout_room = ? and id < ? order by id desc)은
--     이미 최적이다. InnoDB 세컨더리 인덱스에는 PK 가 암묵적으로 뒤에 붙으므로 FK 인덱스
--     (workout_room) 이 물리적으로 (workout_room, id) 이다. 별도 인덱스는 순수 중복이 된다.
--   * workout_room.is_active : boolean 이고 대부분의 행이 true 라 선택도가 낮다.
--   * 관리자 검색(LIKE '%q%') : 선행 와일드카드라 B-Tree 로 처리 불가. 저빈도이므로 방치하고,
--     데이터가 커지면 FULLTEXT 를 별도 검토한다.

-- ---------------------------------------------------------------------------
-- member : 소셜 로그인 조회
-- ---------------------------------------------------------------------------
-- findByOauthProviderAndOauthProviderId 는 모든 소셜 로그인 요청의 hot path 인데
-- 인덱스가 없어 매번 member 풀스캔이 발생한다.
--
-- UNIQUE 로 거는 이유: 코드가 이미 유일성을 전제한다(Optional 반환 — 중복이 있으면
-- NonUniqueResultException 으로 터진다). 또한 MySQL 의 UNIQUE 인덱스는 NULL 을 중복 허용하므로,
-- 이메일 가입 회원(oauth_provider IS NULL)이 아무리 많아도 충돌하지 않는다.
CREATE UNIQUE INDEX `uk_member_oauth`
    ON `member` (`oauth_provider`, `oauth_provider_id`)
    ALGORITHM = INPLACE LOCK = NONE;

-- ---------------------------------------------------------------------------
-- email_verification : 이메일 인증 코드 조회
-- ---------------------------------------------------------------------------
-- findFirstByEmailAndIsVerifiedFalseOrderByCreatedAtDesc / deleteByEmailAndIsVerifiedFalse.
-- 현재 PK 외에 인덱스가 전혀 없고, 이 테이블은 회원가입 시도마다 누적되므로 시간이 갈수록 악화된다.
-- (등호 2개 → 정렬 1개) 순서라 filesort 까지 제거된다.
CREATE INDEX `idx_email_verification_email_verified_created`
    ON `email_verification` (`email`, `is_verified`, `created_at`)
    ALGORITHM = INPLACE LOCK = NONE;

-- ---------------------------------------------------------------------------
-- workout_record : 회원별 일자별 최신 기록 조회
-- ---------------------------------------------------------------------------
-- findAllByMemberPerWorkoutDate 계열 3개 메서드의 상관 서브쿼리가 핵심이다:
--     max(created_at) where member = ? and workout_date = ?
--
-- 기존 UK 는 (member_id, workout_room_id, workout_date) 라 workout_room_id 를 건너뛰게 되어
-- workout_date 를 탈 수 없다. 이 인덱스는 서브쿼리를 index-only 로 만들고
-- order by workout_date desc 도 함께 커버한다.
-- findByMemberAndWorkoutDateAndWorkoutRoomIn 도 같은 인덱스로 해결된다.
CREATE INDEX `idx_workout_record_member_date_created`
    ON `workout_record` (`member_id`, `workout_date`, `created_at`)
    ALGORITHM = INPLACE LOCK = NONE;

-- 주간 벌금 정산 배치의 핵심 쿼리:
--     countByWorkoutRoomAndWorkoutDateBetweenGroupByMember
-- FK 인덱스 (workout_room_id) 만 타서 해당 방의 전체 기간 레코드를 읽고 있다.
-- 이 컬럼 순서면 workout_date 범위 스캔이 가능하고, member_id 까지 포함되어
-- group by 가 테이블 접근 없이 커버링 인덱스만으로 끝난다.
CREATE INDEX `idx_workout_record_room_date_member`
    ON `workout_record` (`workout_room_id`, `workout_date`, `member_id`)
    ALGORITHM = INPLACE LOCK = NONE;
