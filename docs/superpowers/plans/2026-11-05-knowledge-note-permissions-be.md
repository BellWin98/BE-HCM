# 지식 메모 권한·공개 범위 (BE) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 지식 메모에 회원별 조회/작성 권한과 메모별 공개/비공개를 도입하고, 권한과 필터(`scopes`)에 맞춰 서버에서 노출 범위를 강제한다.

**Architecture:**
- 권한은 `toss_access` 패턴을 따라 `knowledge_access`(회원당 1행)에 둡니다.
- 판정은 `KnowledgeAccessChecker`가 `KnowledgePermissions` 값 객체 하나로 계산합니다.
  - 컨트롤러의 `@PreAuthorize`는 "동작 가능 여부"만 봅니다.
  - 서비스는 그 값 객체로 노출 범위(Specification/JPQL)와 소유권을 판정합니다.
- 관리자 API는 `AdminTossAccess*`와 같은 구조로 둡니다.

**Tech Stack:** Spring Boot 4.1 / Java 25, Spring Data JPA(Specification), Spring Security `@PreAuthorize`, Flyway(MySQL), JUnit 5 + Mockito + MockMvc

**Spec:** `docs/superpowers/specs/2026-11-05-knowledge-note-permissions-design.md`

## Global Constraints

**Flyway**
- 버전은 V15, V16을 씁니다.
- 파일 하나에 DDL 하나만 넣습니다.
- 기존 테이블을 `ALTER`할 때는 `ALGORITHM = INPLACE, LOCK = NONE`을 붙입니다.
- 이미 적용된 마이그레이션 파일은 수정하지 않습니다.
- 엔티티의 `@Table` 인덱스·제약 이름은 마이그레이션과 똑같이 맞춥니다.

**코드 규칙**
- 인가는 `KnowledgeAccessChecker` 한 곳에서만 판정합니다. `SecurityConfig` URL 규칙은 건드리지 않습니다.
- 응답은 `ApiResponse<T>`로 감쌉니다.
- 오류는 `CustomException(ErrorCode.X)`로 던집니다. 새 코드는 `ErrorCode`의 `// Knowledge Note` 블록에 한국어 메시지로 추가합니다.
- 로깅은 `@Slf4j`를 씁니다. 권한 변경, 그리고 남의 메모를 수정·삭제할 때는 `log.info` 감사 로그를 남깁니다.
- DI는 `@RequiredArgsConstructor` 생성자 주입으로 합니다.

**테스트**
- 컨텍스트 테스트는 `IntegrationTestSupport`를 상속합니다.
- 새 목 빈은 `IntegrationTestSupport`에만 추가합니다. 하위 클래스에 `@MockitoBean`을 붙이지 않습니다.
- 검증은 `./gradlew test --tests "..."`로 하고, 마지막에 `./gradlew build`를 돌립니다.

**Git**
- 브랜치는 `feature/issue-{N}`, base는 `main`입니다.
- 커밋 메시지는 한국어로 쓰고 끝에 `(#{N})`을 붙입니다. 아래 커밋 예시의 `#N`은 이슈 번호로 바꿉니다.

**권한 규칙 (스펙 2절)**
- ADMIN은 행 없이 모든 권한을 가집니다.
- 일반 유저는 role과 무관하게 `can_read`, `can_write`만 봅니다.
- 다른 유저의 비공개 메모는 일반 유저에게 어떤 경로로도 노출되지 않습니다.

## Review Focus

1. **`stock-counts`로 비공개 메모가 새는 경우**: 종목 배지 숫자에 다른 유저의 비공개 메모가 집계되면 안 됩니다. ADMIN도 마찬가지입니다. → Task 3 테스트 `countVisible_excludesOthersPrivate`
2. **일반 유저가 `scopes=PRIVATE`를 직접 보내는 경우**: 본인 비공개 메모만 나와야 합니다. → Task 3 테스트 `visibleTo_nonAdminPrivateScope_onlyOwnPrivate`
3. **관리자가 남의 메모를 수정한 경우**: 작성자는 그대로이고, 공개 여부를 생략하면 기존 값이 유지돼야 합니다. → Task 4 테스트 `update_byAdmin_keepsAuthorAndVisibility`
4. **수정할 때 기존 이미지를 그대로 두는 경우**: 자기 메모에 이미 붙은 URL은 "다른 메모에서 사용 중" 검사에 걸리면 안 됩니다. → Task 5 테스트 `update_keepingOwnImages_passes`
5. **권한 행이 없는 회원에게 PUT이 동시에 두 번 오는 경우**: 500이 나지 않아야 합니다(upsert). → Task 7 테스트 `update_twiceForNewMember_keepsSingleRow`

---

### Task 1: `knowledge_access` 테이블과 엔티티

**Files:**
- Create: `src/main/resources/db/migration/V15__create_knowledge_access.sql`
- Create: `src/main/java/com/behcm/domain/knowledge/entity/KnowledgeAccess.java`
- Create: `src/main/java/com/behcm/domain/knowledge/repository/KnowledgeAccessRepository.java`
- Test: `src/test/java/com/behcm/domain/knowledge/repository/KnowledgeAccessRepositoryTest.java`

**Interfaces:**
- Produces:
  - `KnowledgeAccess` 엔티티
    - 필드: `Member member`, `boolean canRead`, `boolean canWrite`, `Long grantedBy`
    - `BaseTimeEntity`를 상속합니다.
    - 테이블 이름은 `knowledge_access`이고, UK는 `uk_knowledge_access_member(member_id)`입니다.
  - `KnowledgeAccessRepository extends JpaRepository<KnowledgeAccess, Long>`
    - `Optional<KnowledgeAccess> findByMemberId(Long memberId)`
    - `List<KnowledgeAccess> findByMemberIdIn(Collection<Long> memberIds)`
    - `void deleteByMemberId(Long memberId)`
    - `@Query("SELECT ka FROM KnowledgeAccess ka JOIN FETCH ka.member ORDER BY ka.updatedAt DESC") List<KnowledgeAccess> findAllWithMember()`
    - `@Modifying @Query(nativeQuery = true) int upsert(Long memberId, boolean canRead, boolean canWrite, Long grantedBy)`
      - SQL은 `INSERT INTO knowledge_access (created_at, updated_at, member_id, can_read, can_write, granted_by) VALUES (NOW(6), NOW(6), :memberId, :canRead, :canWrite, :grantedBy) ON DUPLICATE KEY UPDATE can_read = VALUES(can_read), can_write = VALUES(can_write), granted_by = VALUES(granted_by), updated_at = NOW(6)`입니다.

- [ ] **Step 1: Write the failing test** — 테스트는 `IntegrationTestSupport`를 상속하고 `@Transactional`을 붙입니다(`KnowledgeNoteRepositoryTest`와 같은 셋업).
  - `upsert_insertsThenUpdatesSingleRow`
    - 회원을 저장하고 `upsert(id, true, false, 9L)`를 호출한 뒤 `upsert(id, false, true, 9L)`를 호출합니다.
    - `em.clear()` 후 `findByMemberId`의 결과가 `canRead=false`, `canWrite=true`인지 확인합니다.
    - `count()`가 1인지 확인합니다.
  - `findByMemberIdIn_returnsOnlyGranted`
    - 세 회원 중 한 명만 upsert합니다.
    - 결과 크기가 1이고 그 회원의 id가 맞는지 확인합니다.

- [ ] **Step 2: Run test to verify it fails**
  - Run: `./gradlew test --tests "com.behcm.domain.knowledge.repository.KnowledgeAccessRepositoryTest"`
  - Expected: 컴파일 실패(`KnowledgeAccess` 없음)

- [ ] **Step 3: V15와 엔티티, 리포지토리를 구현합니다**
  - V15의 컬럼은 스펙 5.1절과 같습니다.
    - `id`, `created_at datetime(6) NOT NULL`, `updated_at datetime(6)`
    - `member_id bigint NOT NULL`
    - `can_read bit(1) NOT NULL DEFAULT b'0'`, `can_write bit(1) NOT NULL DEFAULT b'0'`
    - `granted_by bigint DEFAULT NULL`
    - `UNIQUE KEY uk_knowledge_access_member (member_id)`
    - `CONSTRAINT fk_knowledge_access_member FOREIGN KEY (member_id) REFERENCES member (id)`
  - 파일 상단 주석은 V8 형식을 따릅니다. 설계 의도(ADMIN은 행이 없음, 두 권한이 모두 false면 행을 삭제, 시드 없음, granted_by는 FK 없음)를 적습니다.
  - 엔티티 주석은 `TossAccess`처럼 설계 의도를 짧게 적습니다.
  - upsert 쿼리에는 `@Modifying(clearAutomatically = true)`를 붙입니다.

- [ ] **Step 4: Run test to verify it passes**
  - Run: Step 2와 같은 명령
  - Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add src/main/resources/db/migration/V15__create_knowledge_access.sql src/main/java/com/behcm/domain/knowledge/entity/KnowledgeAccess.java src/main/java/com/behcm/domain/knowledge/repository/KnowledgeAccessRepository.java src/test/java/com/behcm/domain/knowledge/repository/KnowledgeAccessRepositoryTest.java
git commit -m "feat: 지식 메모 접근 권한 테이블 추가 (#N)"
```

---

### Task 2: 메모 공개 범위 컬럼

**Files:**
- Create: `src/main/resources/db/migration/V16__add_knowledge_note_visibility.sql`
- Create: `src/main/java/com/behcm/domain/knowledge/entity/KnowledgeVisibility.java`
- Modify: `src/main/java/com/behcm/domain/knowledge/entity/KnowledgeNote.java`
- Test: `src/test/java/com/behcm/domain/knowledge/repository/KnowledgeNoteRepositoryTest.java`, `src/test/java/com/behcm/domain/knowledge/entity/KnowledgeNoteTest.java`

**Interfaces:**
- Produces:
  - `enum KnowledgeVisibility { PUBLIC, PRIVATE }`
  - `KnowledgeNote` 변경 사항
    - 필드: `@Enumerated(STRING) @Column(nullable = false, length = 10) KnowledgeVisibility visibility`
    - 빌더에 `visibility` 파라미터를 추가합니다. 값이 null이면 `PUBLIC`이 됩니다.
    - `update(...)`의 마지막 파라미터로 `KnowledgeVisibility visibility`를 추가합니다.
    - `boolean isWrittenBy(Long memberId)`를 추가합니다.

- [ ] **Step 1: Write the failing tests**
  - `KnowledgeNoteTest.builder_withoutVisibility_defaultsToPublic`
    - `visibility` 없이 빌드하면 `getVisibility()`가 `PUBLIC`이어야 합니다.
  - `KnowledgeNoteRepositoryTest.legacyRowWithoutVisibility_readsAsPublic`
    - `em.createNativeQuery("INSERT INTO knowledge_note (created_at, member_id, category, content) VALUES (NOW(6), ?, 'ETC', 'x')")`로 행을 넣습니다.
    - 그 뒤 `findById(...).getVisibility()`가 `PUBLIC`이어야 합니다. 이 테스트로 마이그레이션 DEFAULT를 고정합니다.

- [ ] **Step 2: Run tests to verify they fail**
  - Run: `./gradlew test --tests "com.behcm.domain.knowledge.entity.KnowledgeNoteTest" --tests "com.behcm.domain.knowledge.repository.KnowledgeNoteRepositoryTest"`
  - Expected: 컴파일 실패

- [ ] **Step 3: V16과 엔티티 변경을 구현합니다**
  - V16의 DDL은 다음과 같습니다.
    ```sql
    ALTER TABLE `knowledge_note` ADD COLUMN `visibility` varchar(10) NOT NULL DEFAULT 'PUBLIC', ALGORITHM = INPLACE, LOCK = NONE;
    ```
  - 주석에는 "기존 메모를 공개로 두는 것은 의도적이다(배포 직후 조회 권한자가 관리자 메모를 바로 보게)"라는 내용을 적습니다.
  - `update`를 호출하는 기존 코드(`KnowledgeNoteService.update`)는 이 태스크에서는 `note.getVisibility()`를 넘기도록만 맞춥니다. 실제 동작은 Task 4에서 바꿉니다.
  - 엔티티 클래스 주석의 "관리자 전용"은 "권한이 부여된 회원이 작성"으로 고칩니다.

- [ ] **Step 4: Run tests to verify they pass**
  - Run: Step 2와 같은 명령
  - Expected: PASS(기존 테스트 포함)

- [ ] **Step 5: Commit**
  - 메시지: `feat: 지식 메모 공개 범위 컬럼 추가 (#N)`

---

### Task 3: 권한 값 객체, checker 확장, 노출 범위 쿼리

**Files:**
- Create: `src/main/java/com/behcm/domain/knowledge/service/KnowledgePermissions.java`
- Create: `src/main/java/com/behcm/domain/knowledge/repository/KnowledgeNoteScope.java`
- Modify: `src/main/java/com/behcm/domain/knowledge/service/KnowledgeAccessChecker.java`
- Modify: `src/main/java/com/behcm/domain/knowledge/repository/KnowledgeNoteSpecs.java`
- Modify: `src/main/java/com/behcm/domain/knowledge/repository/KnowledgeNoteRepository.java`
- Test: `KnowledgeAccessCheckerTest.java`, `KnowledgeNoteRepositoryTest.java`(기존 파일)

**Interfaces:**
- Consumes: Task 1 `KnowledgeAccessRepository.findByMemberId`, Task 2 `KnowledgeVisibility`
- Produces:
  - `record KnowledgePermissions(boolean admin, boolean canRead, boolean canWrite)`
    - 상수: `static final KnowledgePermissions NONE = (false, false, false)`, `ADMIN = (true, true, true)`
    - `boolean hasAccess()`: `admin || canRead || canWrite`
    - `boolean seesOthersPublic()`: `admin || canRead`
    - `boolean canEdit(KnowledgeNote note, Long viewerId)`: `admin || (canWrite && note.isWrittenBy(viewerId))`
  - `enum KnowledgeNoteScope { MINE, PUBLIC, PRIVATE }`
  - `KnowledgeAccessChecker`
    - 생성자 주입으로 `KnowledgeAccessRepository`를 받습니다.
    - `@Transactional(readOnly = true) KnowledgePermissions permissionsOf(Object principal)`
      - Member가 아니거나 id가 null이면 `NONE`을 반환합니다.
      - ADMIN이면 `ADMIN`을 반환합니다.
      - 그 외에는 행의 플래그를 반환하고, 행이 없으면 `NONE`을 반환합니다.
    - `boolean canAccess(Object principal)`: `permissionsOf(principal).hasAccess()`
    - `boolean canWrite(Object principal)`: `permissionsOf(principal).canWrite()`
  - `KnowledgeNoteSpecs`
    - `static Specification<KnowledgeNote> visibleTo(Long viewerId, KnowledgePermissions p, Set<KnowledgeNoteScope> scopes)`
    - `static Specification<KnowledgeNote> matches(KnowledgeCategory category, String stockSymbol, List<String> terms)`: 기존 `search`에서 member 조건을 뺀 것이고, `search`는 이것으로 대체합니다.
  - `KnowledgeNoteRepository`
    - `findByIdAndMemberId`를 제거하고 아래 두 메서드로 대체합니다.
    - `@Query("SELECT n FROM KnowledgeNote n WHERE n.id = :id AND (n.member.id = :viewerId OR (:includePublic = true AND n.visibility = com.behcm.domain.knowledge.entity.KnowledgeVisibility.PUBLIC))") Optional<KnowledgeNote> findVisibleById(Long id, Long viewerId, boolean includePublic)`
    - `countByStockSymbols`는 `countVisibleByStockSymbols(Long viewerId, boolean includePublic, Collection<String> symbols)`로 바꿉니다. WHERE 절에는 `findVisibleById`와 같은 공개 범위 조건을 넣습니다.

`visibleTo` 규칙(스펙 5.2절)은 아래와 같습니다. 시그니처만으로는 정해지지 않으므로 그대로 따릅니다.
```
base   = admin ? (조건 없음) : (p.canRead ? mine OR public : mine)
picked = scopes 비어 있음 ? (admin ? {MINE, PUBLIC} : 조건 없음) : scopes 각각(MINE→mine, PUBLIC→public, PRIVATE→private)을 OR
result = base AND picked
```

- [ ] **Step 1: Write the failing tests**
  - `KnowledgeAccessCheckerTest`
    - 리포지토리를 목으로 둡니다. 기존 테스트는 생성자 변경에 맞춰 고칩니다.
    - `permissionsOf_admin_returnsAdminWithoutQuery`: ADMIN이면 `admin=true`이고, 리포지토리를 호출하지 않아야 합니다.
    - `permissionsOf_userWithRow_returnsFlags`: USER와 FAMILY 각각 `(false, true, false)` 행이 있으면 `canRead=true`, `canWrite=false`여야 합니다.
    - `permissionsOf_userWithoutRow_returnsNone`
    - `canAccess_anonymousString_false`: principal이 `"anonymousUser"`이면 false여야 합니다.
  - `KnowledgeNoteRepositoryTest`
    - 공통 데이터: viewer(일반), other(일반), admin 회원을 둡니다. 각자 PUBLIC 1건, PRIVATE 1건씩, 총 6건의 메모를 만듭니다. 모두 종목 `005930`으로 둡니다.
    - 결과는 메모 id 집합으로 비교합니다.

    | 테스트 | 호출 | 기대 결과 |
    |---|---|---|
    | `visibleTo_reader_ownAndOthersPublic` | `p=(false,true,false)`, scopes 비어 있음 | viewer의 2건 + other의 PUBLIC + admin의 PUBLIC |
    | `visibleTo_writerOnly_ownOnly` | `p=(false,false,true)` | viewer의 2건 |
    | `visibleTo_nonAdminPrivateScope_onlyOwnPrivate` | reader, `{PRIVATE}` | viewer의 PRIVATE만 |
    | `visibleTo_nonAdminMineScope_ownOnly` | reader, `{MINE}` | viewer의 2건 |
    | `visibleTo_adminDefault_publicPlusOwnPrivate` | admin, 비어 있음 | PUBLIC 3건 + admin의 PRIVATE |
    | `visibleTo_adminPrivateScope_allPrivate` | admin, `{PRIVATE}` | PRIVATE 3건 |
    | `visibleTo_adminMineAndPrivate_union` | admin, `{MINE, PRIVATE}` | admin의 2건 + 나머지 PRIVATE 2건 |
    | `visibleTo_adminAllScopes_everything` | admin, 세 값 모두 | 6건 |
    | `countVisible_excludesOthersPrivate` | `countVisibleByStockSymbols(viewerId, true, {"005930"})` | 4 |
    | `countVisible_adminSeesOwnAndPublic` | `countVisibleByStockSymbols(adminId, true, ...)` | 4(admin의 PRIVATE 포함, 남의 PRIVATE 제외) |
    | `findVisibleById_othersPrivate_empty` | `findVisibleById(otherPrivateId, viewerId, true)` | 비어 있음 |

    - 기존 `search` 관련 테스트는 `visibleTo(...).and(matches(...))`로 옮깁니다.

- [ ] **Step 2: Run tests to verify they fail**
  - Run: `./gradlew test --tests "com.behcm.domain.knowledge.service.KnowledgeAccessCheckerTest" --tests "com.behcm.domain.knowledge.repository.KnowledgeNoteRepositoryTest"`
  - Expected: 컴파일 실패

- [ ] **Step 3: 위 Interfaces대로 구현합니다**
  - `KnowledgeAccessChecker` 클래스 주석의 "현재는 ADMIN 만 허용"은 새 규칙으로 고칩니다.
  - `KnowledgeNoteService`는 이 태스크에서는 컴파일만 되도록 맞춥니다. 임시로 `KnowledgePermissions.ADMIN` 기준 범위를 쓰고, 실제 동작은 Task 4에서 바꿉니다.

- [ ] **Step 4: Run tests to verify they pass**
  - Run: Step 2와 같은 명령
  - Expected: PASS

- [ ] **Step 5: Commit**
  - 메시지: `feat: 지식 메모 권한 판정과 노출 범위 조회 추가 (#N)`

---

### Task 4: 서비스 — 범위 적용, 소유권, 공개 여부, 응답 확장

**Files:**
- Modify: `src/main/java/com/behcm/domain/knowledge/service/KnowledgeNoteService.java`
- Modify: `src/main/java/com/behcm/domain/knowledge/dto/KnowledgeNoteRequest.java`
- Modify: `src/main/java/com/behcm/domain/knowledge/dto/KnowledgeNoteResponse.java`
- Create: `src/main/java/com/behcm/domain/knowledge/dto/KnowledgeNoteAuthor.java`
- Modify: `src/main/java/com/behcm/global/exception/ErrorCode.java`
- Test: `src/test/java/com/behcm/domain/knowledge/service/KnowledgeNoteServiceTest.java`

**Interfaces:**
- Consumes: Task 3 `KnowledgeAccessChecker.permissionsOf`, `KnowledgePermissions`, `KnowledgeNoteSpecs.visibleTo` / `matches`, `findVisibleById`, `countVisibleByStockSymbols`
- Produces:
  - `KnowledgeNoteRequest.visibility`(`KnowledgeVisibility`, nullable)
  - `record KnowledgeNoteAuthor(Long id, String nickname, String profileUrl)`
  - `KnowledgeNoteResponse`
    - 기존 필드 뒤에 `KnowledgeVisibility visibility`, `KnowledgeNoteAuthor author`, `boolean editable`를 추가합니다.
    - 팩토리를 `static KnowledgeNoteResponse of(KnowledgeNote note, Long viewerId, KnowledgePermissions p)`로 바꿉니다(`from` 제거).
  - `ErrorCode.KNOWLEDGE_NOTE_FORBIDDEN(HttpStatus.FORBIDDEN, "본인이 작성한 메모만 수정하거나 삭제할 수 있습니다.")`
  - `KnowledgeNoteService`
    - 생성자에 `KnowledgeAccessChecker`를 추가합니다.
    - `search`에 `Set<KnowledgeNoteScope> scopes` 파라미터를 추가합니다(`stockSymbol` 다음, `page` 앞).
    - 나머지 public 시그니처는 그대로 둡니다.

**동작**
- 각 public 메서드는 시작할 때 `permissionsOf(member)`를 한 번만 부릅니다.
- `get`, `update`, `delete`는 ADMIN이면 `findById`로, 아니면 `findVisibleById(id, me, p.seesOthersPublic())`로 메모를 찾습니다. 찾지 못하면 `KNOWLEDGE_NOTE_NOT_FOUND`를 던집니다.
- `update`, `delete`는 찾은 뒤 `!p.canEdit(note, me)`이면 `KNOWLEDGE_NOTE_FORBIDDEN`을 던집니다.
- `create`는 `request.visibility ?? PUBLIC`을 씁니다.
- `update`는 `request.visibility ?? note.getVisibility()`를 씁니다. 작성자(`member`)는 바꾸지 않습니다.
- `update`와 `delete`의 로그에는 `actorId`와 `authorId`를 함께 남깁니다.
- `countByStockSymbols`는 `countVisibleByStockSymbols(me, p.seesOthersPublic(), ...)`를 씁니다.
- 클래스 주석의 "본인 메모로 한정"과 "ADMIN 판정은 컨트롤러 책임"은 새 규칙으로 고칩니다.

- [ ] **Step 1: Write the failing tests**
  - checker는 목으로 두고 `permissionsOf`의 반환값을 테스트마다 지정합니다.
  - 기존 테스트의 `findByIdAndMemberId` 스텁은 `findVisibleById`로 바꿉니다.
  - `create_withoutVisibility_savesPublic`, `create_withPrivate_savesPrivate`
  - `get_nonAdmin_usesVisibleLookup`: `(false,true,false)`이면 `findVisibleById(id, me, true)`를 호출하고 `findById`는 호출하지 않아야 합니다.
  - `get_admin_findsAnyNote`: ADMIN이면 `findById`를 호출해야 합니다.
  - `update_othersPublicNote_throwsForbidden`: writer가 남의 PUBLIC 메모를 수정하면 `KNOWLEDGE_NOTE_FORBIDDEN`이 나고, 저장과 이미지 정리를 하지 않아야 합니다.
  - `delete_othersPublicNote_throwsForbidden`
  - `update_byAdmin_keepsAuthorAndVisibility`
    - ADMIN이 남의 PRIVATE 메모를 `visibility=null`로 수정합니다.
    - 응답의 `author.id`는 원 작성자이고, `visibility`는 `PRIVATE`이어야 합니다.
  - `update_ownNote_changesVisibility`: writer가 자기 메모를 PRIVATE으로 바꾸면 응답 `visibility`가 PRIVATE이어야 합니다.
  - `search_responseEditable_followsPermissions`
    - reader 응답의 `editable`은 모두 false여야 합니다.
    - writer는 자기 메모만 true여야 합니다.
    - ADMIN은 모두 true여야 합니다.
  - `countByStockSymbols_readerPassesIncludePublicTrue`, `countByStockSymbols_writerOnlyPassesIncludePublicFalse`

- [ ] **Step 2: Run tests to verify they fail**
  - Run: `./gradlew test --tests "com.behcm.domain.knowledge.service.KnowledgeNoteServiceTest"`
  - Expected: FAIL 또는 컴파일 실패

- [ ] **Step 3: 위 동작대로 구현합니다**
  - `editable` 계산에 쓰는 `note.getMember()` 접근은 `default_batch_fetch_size: 500`으로 배치 로딩되므로 별도 fetch join은 넣지 않습니다.

- [ ] **Step 4: Run tests to verify they pass**
  - Run: Step 2와 같은 명령
  - Expected: PASS

- [ ] **Step 5: Commit**
  - 메시지: `feat: 지식 메모 권한별 조회 범위와 소유권 검사 적용 (#N)`

---

### Task 5: 이미지 URL 재사용 차단과 공유 URL 보호

**Files:**
- Modify: `src/main/java/com/behcm/domain/knowledge/repository/KnowledgeNoteRepository.java`
- Modify: `src/main/java/com/behcm/domain/knowledge/service/KnowledgeNoteService.java`
- Modify: `src/main/java/com/behcm/domain/admin/member/service/AdminMemberService.java`
- Test: `KnowledgeNoteServiceTest.java`, `KnowledgeNoteRepositoryTest.java`, `AdminMemberServiceTest.java`

**Interfaces:**
- Consumes: Task 4 서비스
- Produces (`KnowledgeNoteRepository`):
  - `@Query("SELECT DISTINCT i.imageUrl FROM KnowledgeNoteImage i WHERE i.imageUrl IN :urls AND i.note.id <> :noteId") Set<String> findUrlsUsedByOtherNotes(Collection<String> urls, Long noteId)`
    - 생성할 때는 `noteId`로 `-1L`을 넘깁니다.
  - `@Query("SELECT DISTINCT i.imageUrl FROM KnowledgeNoteImage i WHERE i.imageUrl IN :urls AND i.note.member.id <> :memberId") Set<String> findUrlsUsedByOtherMembers(Collection<String> urls, Long memberId)`

**동작**
- `create`: 요청 URL 중 하나라도 `findUrlsUsedByOtherNotes(urls, -1L)`에 걸리면 `KNOWLEDGE_NOTE_IMAGE_INVALID`를 던집니다.
- `update`
  - 요청 URL 중 이 메모에 원래 없던 것만 같은 검사를 합니다. 이때 `noteId`는 이 메모의 id입니다.
  - 정리 대상(`removedImages`)에서 `findUrlsUsedByOtherNotes(removed, id)`에 나온 URL은 뺍니다.
- `delete`: 정리 대상에서 다른 메모가 쓰는 URL을 뺍니다.
- `AdminMemberService.deleteMember`: 회원의 이미지 URL에서 `findUrlsUsedByOtherMembers(urls, memberId)`에 나온 URL을 뺀 뒤 정리를 예약합니다.
- 빈 컬렉션이면 쿼리를 호출하지 않습니다. JPQL의 `IN ()`는 피합니다.

- [ ] **Step 1: Write the failing tests**
  - `create_withUrlUsedByOtherNote_throwsImageInvalid`
  - `update_keepingOwnImages_passes`: 기존 URL을 그대로 보내면 사용 중 검사 쿼리에 그 URL을 넘기지 않고 성공해야 합니다.
  - `update_removedUrlStillUsedElsewhere_notCleaned`: 빠진 URL이 다른 메모에서 쓰이면 `imageCleaner.deleteAfterCommit`에 그 URL이 들어가지 않아야 합니다.
  - `delete_sharedUrl_notCleaned`
  - `KnowledgeNoteRepositoryTest.findUrlsUsedByOtherNotes_excludesSelf`: 같은 URL이 메모 A와 B에 있으면, A 기준 결과에는 그 URL이 있고 B만 가진 다른 URL은 A 기준 결과에 없어야 합니다.
  - `AdminMemberServiceTest.deleteMember_sharedImageWithOtherMember_notCleaned`

- [ ] **Step 2: Run tests to verify they fail**
  - Run: `./gradlew test --tests "com.behcm.domain.knowledge.*" --tests "com.behcm.domain.admin.member.service.AdminMemberServiceTest"`
  - Expected: FAIL

- [ ] **Step 3: 위 동작대로 구현합니다**

- [ ] **Step 4: Run tests to verify they pass**
  - Run: Step 2와 같은 명령
  - Expected: PASS

- [ ] **Step 5: Commit**
  - 메시지: `feat: 지식 메모 이미지 재사용 차단 및 공유 이미지 보호 (#N)`

---

### Task 6: 컨트롤러 — 동작별 인가, `scopes`, `/access` 확장

**Files:**
- Modify: `src/main/java/com/behcm/domain/knowledge/controller/KnowledgeNoteController.java`
- Modify: `src/main/java/com/behcm/domain/knowledge/controller/KnowledgeAccessController.java`
- Modify: `src/main/java/com/behcm/domain/knowledge/dto/KnowledgeAccessResponse.java`
- Test: `KnowledgeNoteControllerTest.java`, `KnowledgeAccessControllerTest.java`

**Interfaces:**
- Consumes: Task 3 checker(`canAccess`, `canWrite`, `permissionsOf`), Task 4 `search(..., Set<KnowledgeNoteScope> scopes, page, size)`
- Produces (FE 계약):
  - `GET /api/knowledge-notes/access` → `{ hasAccess, canRead, canWrite, isAdmin }`
    - DTO는 `record KnowledgeAccessResponse(boolean hasAccess, boolean canRead, boolean canWrite, boolean isAdmin)`이고 `static from(KnowledgePermissions)`를 둡니다.
    - ADMIN이면 `canRead`와 `canWrite`가 모두 true입니다.
  - `GET /api/knowledge-notes?scopes=MINE&scopes=PUBLIC`
    - 컨트롤러 파라미터는 `@RequestParam(required = false) Set<KnowledgeNoteScope> scopes`입니다.
    - null이면 빈 Set으로 넘깁니다.
  - `POST /`, `POST /images`, `PUT /{id}`, `DELETE /{id}`에는 메서드 레벨 `@PreAuthorize("@knowledgeAccessChecker.canWrite(principal)")`를 붙입니다.
  - 클래스 레벨 `canAccess`는 유지합니다.

- [ ] **Step 1: Write the failing tests**
  - checker 목은 `canAccess`와 `canWrite`를 각각 스텁합니다.
  - `write_whenCanAccessButNotWrite_returns403`: POST, PUT, DELETE, multipart `/images` 모두 403이어야 하고, 서비스와 상호작용이 없어야 합니다.
  - `read_whenCanAccessButNotWrite_returns200`: `GET /`와 `GET /{id}`는 200이어야 합니다.
  - `search_bindsScopes`
    - `?scopes=MINE&scopes=PRIVATE`이면 서비스가 `Set.of(MINE, PRIVATE)`로 호출돼야 합니다.
    - 파라미터가 없으면 `Set.of()`로 호출돼야 합니다.
  - `search_invalidScope_returns400`: `?scopes=ALL`이면 400이어야 합니다.
  - `KnowledgeAccessControllerTest.getAccess_returnsAllFlags`: `permissionsOf`가 `(false,true,false)`이면 `$.data.hasAccess=true`, `canRead=true`, `canWrite=false`, `isAdmin=false`여야 합니다.
  - 기존 `response(id)` 헬퍼는 새 레코드 필드(`PUBLIC`, author, `editable`)에 맞춰 고칩니다.

- [ ] **Step 2: Run tests to verify they fail**
  - Run: `./gradlew test --tests "com.behcm.domain.knowledge.controller.*"`
  - Expected: FAIL

- [ ] **Step 3: 위 Interfaces대로 구현합니다**
  - `KnowledgeAccessController`는 `canAccess` 대신 `permissionsOf`를 써서 응답을 만듭니다.
  - enum 바인딩이 실패할 때 400이 나는지는 기존 `GlobalExceptionHandler`의 `MethodArgumentTypeMismatchException` 처리를 확인합니다. 처리가 없으면 `KnowledgeCategory`의 기존 동작과 같은 수준(400)이 되도록 맞춥니다.

- [ ] **Step 4: Run tests to verify they pass**
  - Run: Step 2와 같은 명령
  - Expected: PASS

- [ ] **Step 5: Commit**
  - 메시지: `feat: 지식 메모 API 동작별 권한 검사와 범위 필터 추가 (#N)`

---

### Task 7: 관리자 권한 관리 API와 회원 목록·삭제 연동

**Files:**
- Create: `src/main/java/com/behcm/domain/admin/knowledgeaccess/controller/AdminKnowledgeAccessController.java`
- Create: `src/main/java/com/behcm/domain/admin/knowledgeaccess/service/AdminKnowledgeAccessService.java`
- Create: `src/main/java/com/behcm/domain/admin/knowledgeaccess/dto/AdminKnowledgeAccessRequest.java`, `AdminKnowledgeAccessResponse.java`
- Create: `src/main/java/com/behcm/domain/admin/member/dto/KnowledgeAccessFlags.java`
- Modify: `src/main/java/com/behcm/domain/admin/member/dto/AdminMemberResponse.java`
- Modify: `src/main/java/com/behcm/domain/admin/member/service/AdminMemberService.java`
- Modify: `src/test/java/com/behcm/support/IntegrationTestSupport.java`(`@MockitoBean AdminKnowledgeAccessService adminKnowledgeAccessService` 추가)
- Test: `src/test/java/com/behcm/domain/admin/knowledgeaccess/service/AdminKnowledgeAccessServiceTest.java`(컨텍스트 + 실 DB), `.../controller/AdminKnowledgeAccessControllerTest.java`, `AdminMemberServiceTest.java`

**Interfaces:**
- Consumes: Task 1 `KnowledgeAccessRepository`(`upsert`, `findByMemberId`, `findByMemberIdIn`, `deleteByMemberId`, `findAllWithMember`)
- Produces (FE 계약):
  - `record AdminKnowledgeAccessRequest(@NotNull Boolean canRead, @NotNull Boolean canWrite)`
  - `AdminKnowledgeAccessResponse`
    - 필드: `Long memberId`, `String email`, `String nickname`, `String profileUrl`, `boolean canRead`, `boolean canWrite`, `Long grantedBy`, `LocalDateTime updatedAt`
    - `@Getter @Builder` 패턴입니다.
  - `GET /api/admin/knowledge-access` → `List<AdminKnowledgeAccessResponse>`
  - `PUT /api/admin/knowledge-access/{memberId}`(본문은 request) → `AdminKnowledgeAccessResponse`
    - 메시지는 `"지식 메모 권한을 변경했습니다."`입니다.
    - 클래스에 `@PreAuthorize("hasRole('ADMIN')")`를 붙입니다.
  - `AdminKnowledgeAccessService.update(Long memberId, AdminKnowledgeAccessRequest request, Member admin)`
    - 대상이 없으면 `MEMBER_NOT_FOUND`를 던집니다.
    - 대상이 ADMIN이면 저장하지 않고, `canRead`와 `canWrite`를 모두 true로 응답합니다.
    - 두 값이 모두 false면 `deleteByMemberId`를 호출하고, 플래그가 false인 응답을 돌려줍니다.
    - 그 외에는 `upsert`를 호출한 뒤 `findByMemberId`로 다시 읽어 응답합니다.
    - `log.info("Admin updated knowledge access (memberId={}, canRead={}, canWrite={}, grantedBy={})", ...)`를 남깁니다.
  - `record KnowledgeAccessFlags(boolean canRead, boolean canWrite)`, 상수 `NONE`
  - `AdminMemberResponse`
    - `KnowledgeAccessFlags knowledgeAccess` 필드를 추가합니다.
    - 팩토리를 `from(Member member, boolean tossAccess, KnowledgeAccessFlags knowledgeAccess)`로 바꿉니다.
  - `AdminMemberService`
    - `getMembers`는 `findByMemberIdIn`으로 페이지 단위 일괄 조회를 합니다.
    - `updateMemberRole`은 `findByMemberId`로 채웁니다.
    - `deleteMember`는 `tossAccessRepository.deleteByMemberId` 다음 줄에서 `knowledgeAccessRepository.deleteByMemberId(memberId)`를 호출합니다.

- [ ] **Step 1: Write the failing tests**
  - `AdminKnowledgeAccessServiceTest`
    - `IntegrationTestSupport`와 `@Transactional`을 쓰고, 실제 서비스 빈을 사용합니다.
    - 목 빈과 이름이 겹치므로, 테스트에서는 `new AdminKnowledgeAccessService(repo, memberRepo)`로 직접 생성합니다.
    - `update_grantRead_createsRow`
    - `update_bothFalse_deletesRow`
    - `update_twiceForNewMember_keepsSingleRow`: 같은 회원에게 다른 값으로 두 번 호출하면 행이 1개이고 마지막 값이어야 합니다.
    - `update_adminTarget_noRowAndAllTrue`
    - `update_unknownMember_throwsMemberNotFound`
  - `AdminKnowledgeAccessControllerTest`
    - `getAndPut_asUser_returns403`
    - `put_asAdmin_delegatesToService`
    - `put_missingField_returns400`: `{"canRead":true}`이면 400이어야 합니다.
  - `AdminMemberServiceTest`
    - `getMembers_fillsKnowledgeAccessInOneQuery`: `findByMemberIdIn`은 1회 호출되고, 행이 없는 회원은 `NONE`이어야 합니다.
    - `deleteMember_deletesKnowledgeAccess`

- [ ] **Step 2: Run tests to verify they fail**
  - Run: `./gradlew test --tests "com.behcm.domain.admin.*"`
  - Expected: FAIL

- [ ] **Step 3: 위 Interfaces대로 구현합니다**
  - `AdminTossAccess*`의 주석과 구조를 따릅니다.

- [ ] **Step 4: Run tests to verify they pass, then full build**
  - Run: `./gradlew test --tests "com.behcm.domain.admin.*"`, 이어서 `./gradlew build`
  - Expected: 모두 PASS, `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**
  - 메시지: `feat: 관리자 지식 메모 권한 관리 API 추가 (#N)`

---

### Task 8: 문서 정리

**Files:**
- Modify: `CLAUDE.md`(BE-HCM)

- [ ] **Step 1: `CLAUDE.md` 아키텍처 섹션에 `domain/knowledge` 항목을 추가합니다**
  - 노출 범위는 `KnowledgeNoteSpecs.visibleTo` / `findVisibleById` / `countVisibleByStockSymbols` 세 곳이 같은 규칙을 공유합니다. 새 조회 경로를 추가할 때는 반드시 이 중 하나를 거쳐야 하며, 그러지 않으면 비공개 메모가 샙니다.
  - 권한 판정은 `KnowledgeAccessChecker.permissionsOf` 한 곳에서 합니다.
  - 이미지 URL은 메모 간 공유를 막고, 정리할 때 다른 메모가 쓰는 URL은 지우지 않습니다.

- [ ] **Step 2: Commit**
  - 메시지: `docs: 지식 메모 권한 구조 문서화 (#N)`
