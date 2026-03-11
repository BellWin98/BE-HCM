-- Performance optimization indexes & cache monitoring guide
-- 대상 DB: MariaDB / MySQL

-- 1. WorkoutRecord 인덱스 (운동 인증, 벌금 배치, 통계 조회)
-- 대상 쿼리 예시:
--  - findAllByMemberPerWorkoutDate(memberId, workoutDate)
--  - findByWorkoutRoomAndMemberIn(workoutRoomId, memberIds, between workoutDate)
-- 기대 효과: 멤버/방/날짜 기반 조회 속도 개선, 벌금 계산 배치의 풀스캔 방지
CREATE INDEX idx_workout_record_member_room_date
    ON workout_record (member_id, workout_room_id, workout_date);

CREATE INDEX idx_workout_record_member_date_created
    ON workout_record (member_id, workout_date, created_at);


-- 2. Rest 인덱스 (결석/휴식 집계, 벌금 계산)
-- 대상 쿼리 예시:
--  - findAllByWorkoutRoomMemberInAndStartDateBetween(members, startDate, endDate)
-- 기대 효과: 방 멤버 리스트 + 기간 조건으로 조회 시 범위 스캔 최적화
CREATE INDEX idx_rest_member_start_date
    ON rest (workout_room_member_id, start_date);


-- 3. Penalty 인덱스 (벌금 조회 및 집계)
-- 대상 쿼리 예시:
--  - findAllByWorkoutRoomIdAndMemberIdIn(roomId, memberIds)
-- 기대 효과: 방/멤버 단위 벌금 조회 및 집계 속도 개선
CREATE INDEX idx_penalty_room_member_created
    ON penalty (workout_room_id, member_id, created_at);


-- 4. Member / WorkoutRoom 인덱스 (관리자 검색)
-- 대상 쿼리 예시:
--  - searchAdminMembers(role, nickname, email LIKE ...)
--  - searchAdminRooms(is_active, name LIKE ..., host_nickname)
-- 기대 효과: ROLE + 닉네임/이메일, 방 활성 상태 + 이름 검색 성능 개선
CREATE INDEX idx_member_role_nickname_email
    ON member (role, nickname, email);

CREATE INDEX idx_workout_room_active_name
    ON workout_room (is_active, name);


-- 5. 모니터링 & 검증 전략 (문서용, 실행 X)
-- 5.1. SQL 로그 기반
--  - application.yml 에 Hibernate SQL / slow query 로그를 활성화
--  - 인덱스 적용 전후로 동일 시나리오(벌금 배치, 프로필 조회, 방 상세 조회)를 실행하여
--    쿼리 횟수와 실행 시간을 비교
--
-- 5.2. Spring Actuator 기반
--  - 의존성: spring-boot-starter-actuator
--  - application.yml:
--      management:
--        endpoints:
--          web:
--            exposure:
--              include: "health,info,metrics"
--        endpoint:
--          metrics:
--            enabled: true
--  - /actuator/metrics 를 통해 다음 항목 모니터링:
--      - hikaricp.connections.*
--      - http.server.requests (핵심 API 응답 시간 분포)
--      - cache.gets / cache.misses (Caffeine 캐시 히트율)
--
-- 5.3. 기준 시나리오
--  - 벌금 배치: PenaltyService 주간 처리 메서드 기준
--  - 회원 프로필 조회: MemberService.getMemberProfile
--  - 방 상세 조회: WorkoutRoomService.getJoinedWorkoutRoom
--  - 주식 포트폴리오 / 현재가 조회: StockService.getStockPortfolio / getStockPrice
--  위 시나리오에 대해, 인덱스/캐시 적용 전후의 평균 응답 시간과 쿼리 수를 비교하여
--  성능 개선 효과를 검증한다.

