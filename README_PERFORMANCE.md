# 데이터베이스 성능 최적화 가이드

## 개요
HCM 프로젝트의 데이터베이스 성능 최적화를 위한 인덱스 추가 및 성능 비교 테스트 가이드입니다.

## 추가된 인덱스 목록

### 🔴 HIGH PRIORITY (즉시 적용 권장)

#### 1. WorkoutRecord 테이블
```sql
-- 벌금 계산 시 가장 빈번하게 사용되는 복합 인덱스
CREATE INDEX idx_workout_record_member_room_date
ON workout_record (member_id, workout_room_id, workout_date);

-- 멤버별 운동 기록 조회용
CREATE INDEX idx_workout_record_member_created
ON workout_record (member_id, created_at);

-- 날짜 범위 검색용
CREATE INDEX idx_workout_record_date_range
ON workout_record (workout_date);
```

#### 2. Penalty 테이블
```sql
-- 미납 벌금 조회용 (방별)
CREATE INDEX idx_penalty_room_paid
ON penalty (workout_room_member_id, is_paid);

-- 전체 미납 벌금 조회용
CREATE INDEX idx_penalty_paid_created
ON penalty (is_paid, created_at);

-- 주차별 벌금 조회용
CREATE INDEX idx_penalty_week_dates
ON penalty (week_start_date, week_end_date);
```

#### 3. ChatMessage 테이블
```sql
-- 채팅방별 메시지 조회용 (최신순)
CREATE INDEX idx_chat_message_room_id_desc
ON chat_message (workout_room_id, id DESC);

-- 특정 ID 이후 메시지 조회용
CREATE INDEX idx_chat_message_room_id_asc
ON chat_message (workout_room_id, id ASC);

-- 타임스탬프 기반 조회용
CREATE INDEX idx_chat_message_room_timestamp
ON chat_message (workout_room_id, timestamp);
```

### 🟡 MEDIUM PRIORITY

#### 4. WorkoutRoom 테이블
```sql
CREATE INDEX idx_workout_room_active ON workout_room (is_active);
CREATE INDEX idx_workout_room_entry_code ON workout_room (entry_code);
CREATE INDEX idx_workout_room_active_end_date ON workout_room (is_active, end_date);
```

#### 5. WorkoutRoomMember 테이블
```sql
CREATE INDEX idx_workout_room_member_member ON workout_room_member (member_id);
CREATE INDEX idx_workout_room_member_room_joined ON workout_room_member (workout_room_id, joined_at);
CREATE INDEX idx_workout_room_member_member_room ON workout_room_member (member_id, workout_room_id);
```

## 인덱스 적용 방법

### 1. 자동 적용 (권장)
Flyway 마이그레이션을 통한 자동 적용:
```bash
./gradlew flywayMigrate
```

### 2. 수동 적용
직접 SQL 실행:
```bash
# 마이그레이션 파일 실행
mysql -u username -p database_name < src/main/resources/db/migration/V2__Add_Performance_Indexes.sql
```

## 성능 테스트 실행

### 1. 기본 성능 테스트
```bash
./gradlew test --tests "DatabasePerformanceTest"
```

### 2. 인덱스 전후 비교 테스트
```bash
./gradlew test --tests "IndexPerformanceComparisonTest"
```

### 3. 실시간 성능 모니터링
- 애플리케이션 실행 시 자동으로 쿼리 성능 모니터링 시작
- 로그에서 성능 메트릭 확인:
```
INFO c.b.g.c.QueryPerformanceConfig - === Database Performance Metrics ===
INFO c.b.g.c.QueryPerformanceConfig - Query execution count: 1250
INFO c.b.g.c.QueryPerformanceConfig - Query execution max time: 45 ms
INFO c.b.g.c.QueryPerformanceConfig - Query execution average time: 12.5 ms
```

## 예상 성능 개선 효과

### WorkoutRecord 테이블 쿼리
- **벌금 계산 쿼리**: 100-500ms → 5-15ms (90% 향상)
- **멤버별 운동 기록**: 50-150ms → 3-10ms (85% 향상)
- **중복 체크**: 20-80ms → 1-5ms (95% 향상)

### Penalty 테이블 쿼리
- **미납 벌금 조회**: 30-100ms → 2-8ms (90% 향상)
- **전체 벌금 통계**: 50-200ms → 5-20ms (85% 향상)

### ChatMessage 테이블 쿼리
- **채팅 메시지 로딩**: 40-120ms → 3-12ms (90% 향상)
- **페이징 조회**: 60-180ms → 5-15ms (88% 향상)

## 모니터링 및 유지보수

### 1. 성능 모니터링
- `/actuator/health` 엔드포인트에서 DB 성능 상태 확인
- 애플리케이션 로그에서 슬로우 쿼리 모니터링
- 100ms 이상 걸리는 쿼리 자동 로깅

### 2. 인덱스 유지보수
```sql
-- 인덱스 사용률 확인 (MariaDB/MySQL)
SELECT
    TABLE_NAME,
    INDEX_NAME,
    SEQ_IN_INDEX,
    COLUMN_NAME,
    CARDINALITY
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'your_database_name'
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

-- 사용되지 않는 인덱스 확인
SHOW INDEX FROM workout_record;
```

### 3. 주의사항
- 인덱스는 SELECT 성능을 향상시키지만 INSERT/UPDATE/DELETE 성능에는 영향을 줄 수 있음
- 정기적으로 인덱스 사용률을 모니터링하여 불필요한 인덱스 제거
- 데이터 증가에 따른 인덱스 재구성 고려

## 트러블슈팅

### 1. 마이그레이션 실패 시
```bash
# 마이그레이션 상태 확인
./gradlew flywayInfo

# 마이그레이션 복구
./gradlew flywayRepair
```

### 2. 성능이 개선되지 않는 경우
- 쿼리 실행 계획 확인
- 통계 정보 업데이트
- 인덱스 힌트 사용 고려

### 3. 메모리 사용량 증가 시
- 인덱스 크기 확인
- 불필요한 인덱스 제거
- 데이터베이스 메모리 설정 조정

## 추가 최적화 방안

1. **쿼리 최적화**: N+1 문제 해결, 배치 조회 적용
2. **캐싱 전략**: Redis를 활용한 자주 조회되는 데이터 캐싱
3. **파티셔닝**: 대용량 테이블의 날짜 기반 파티셔닝
4. **읽기 전용 복제본**: 조회 전용 쿼리의 부하 분산