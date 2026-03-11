## 관리자 검색 인덱스/검색 컬럼 설계

이 문서는 `MemberRepository.searchAdminMembers` 와 `WorkoutRoomRepository.searchAdminRooms` 에 대한
MySQL 기준 인덱스 및 검색 컬럼 설계를 정리한 것입니다.

---

## 1. MemberRepository.searchAdminMembers

대상 메서드:

```java
@Query("SELECT m FROM Member m " +
        "WHERE (:role IS NULL OR m.role = :role) " +
        "AND (:query IS NULL OR LOWER(m.nickname) LIKE LOWER(CONCAT('%', :query, '%')) " +
        "OR LOWER(m.email) LIKE LOWER(CONCAT('%', :query, '%')))")
Page<Member> searchAdminMembers(@Param("query") String query,
                                @Param("role") MemberRole role,
                                Pageable pageable);
```

### 1-1. 기본 인덱스 전략

- **목표**: `role` 로 1차 필터링 후 `nickname` / `email` 로 LIKE 검색 시 불필요한 풀스캔을 줄입니다.
- **전제**: MySQL 8.x, InnoDB 사용.

권장 인덱스:

```sql
-- 역할 + 닉네임/이메일 복합 인덱스
CREATE INDEX idx_member_role_nickname_email
    ON member (role, nickname, email);
```

> 비고: `email`, `nickname` 에는 이미 유니크 인덱스가 존재하지만, `role` 과의 복합 인덱스를 두어
> 관리자 검색(ROLE_ADMIN / ROLE_USER 등) 필터링 후 후속 LIKE 연산의 대상 row 수를 줄이는 것이 목표입니다.

### 1-2. LOWER 기반 검색 최적화를 위한 함수 인덱스

현재 JPQL 은 `LOWER(m.nickname)` / `LOWER(m.email)` 을 사용하므로,
MySQL 8 의 함수 기반 인덱스를 사용하면 추가 컬럼 없이도 검색을 최적화할 수 있습니다.

```sql
-- LOWER(nickname), LOWER(email)을 사용하는 함수 기반 인덱스
CREATE INDEX idx_member_role_lower_nickname_email
    ON member (
        role,
        (LOWER(nickname)),
        (LOWER(email))
    );
```

> 운영 환경에서의 선택:
>
> - 대소문자 구분이 중요하지 않고, JPQL 에서 항상 `LOWER(...)` 를 사용하는 현재 패턴을 유지한다면
>   위 함수 기반 인덱스를 채택합니다.
> - 만약 추후 검색 컬럼을 별도로 두고자 한다면
>   `lower_nickname`, `lower_email` 컬럼을 추가하고 트리거/애플리케이션 로직으로 동기화하는 전략도 가능합니다.

---

## 2. WorkoutRoomRepository.searchAdminRooms

대상 메서드:

```java
@Query(
        """
                SELECT wr
                FROM WorkoutRoom wr
                JOIN wr.owner o
                WHERE (:active IS NULL OR wr.isActive = :active)
                AND (
                    :query IS NULL
                    OR LOWER(wr.name) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(o.nickname) LIKE LOWER(CONCAT('%', :query, '%'))
                )
                """
)
Page<WorkoutRoom> searchAdminRooms(@Param("query") String query,
                                   @Param("active") Boolean active,
                                   Pageable pageable);
```

### 2-1. WorkoutRoom 기본 인덱스 전략

목표:

- `is_active` + 방 이름 기반 검색을 빠르게 수행.
- 방 상태(active 여부) 필터와 이름 검색을 함께 사용하는 관리자 검색 시 풀스캔 최소화.

권장 인덱스:

```sql
-- 활성 여부 + 방 이름 인덱스
CREATE INDEX idx_workout_room_active_name
    ON workout_room (is_active, name);
```

추가로, 방 소유자 기준으로 방을 조회하거나 상태와 함께 자주 필터링한다면 다음 인덱스도 고려할 수 있습니다.

```sql
-- 활성 여부 + owner_id 기준 인덱스
CREATE INDEX idx_workout_room_active_owner
    ON workout_room (is_active, owner_id);
```

### 2-2. LOWER 기반 방 이름 검색 최적화

`LOWER(wr.name)` 을 사용하는 패턴을 그대로 유지하면서 인덱스를 활용하려면 함수 기반 인덱스를 사용할 수 있습니다.

```sql
-- LOWER(name)을 사용하는 함수 기반 인덱스
CREATE INDEX idx_workout_room_active_lower_name
    ON workout_room (
        is_active,
        (LOWER(name))
    );
```

---

## 3. Member.nickname / LOWER(o.nickname) 검색 보조 전략

`WorkoutRoomRepository.searchAdminRooms` 에서는 방장 닉네임(`o.nickname`) 으로 검색합니다.

- `Member.nickname` 은 `@Column(unique = true)` 로 이미 유니크 인덱스가 존재.
- 다만 `LOWER(o.nickname)` 으로 검색하므로, 필요 시 함수 기반 인덱스를 추가할 수 있습니다.

```sql
-- LOWER(nickname)을 사용하는 함수 기반 인덱스
CREATE INDEX idx_member_lower_nickname
    ON member ((LOWER(nickname)));
```

---

## 4. 적용 및 모니터링 가이드

- **적용 방법**
  - 운영 환경에서 사용 중인 마이그레이션 도구(Flyway/Liquibase 등)에
    위 인덱스 DDL 을 순차적으로 추가합니다.
- **검증 포인트**
  - 인덱스 추가 전/후 `searchAdminMembers`, `searchAdminRooms` 호출 시
    - 실행 계획(EXPLAIN)을 확인해 인덱스 사용 여부를 검증합니다.
    - 쿼리 지연 시간 및 스캔 row 수 변화를 모니터링합니다.
- **주의 사항**
  - `%query%` 형태의 선행 와일드카드 LIKE 검색 특성상
    인덱스 효과가 제한적일 수 있으므로, 필요 시
    - 접두사 검색(`query%`)으로의 전환,
    - 별도 검색 컬럼/검색 인덱스 도입,
    - 전문검색(Full-text search) 도입 등을 장기적으로 검토할 수 있습니다.

