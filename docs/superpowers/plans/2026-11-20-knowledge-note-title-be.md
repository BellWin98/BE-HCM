# 지식 메모 제목 (BE) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 지식 메모에 선택 입력 제목(최대 100자)을 저장·반환하고 검색 대상에 포함한다.

**Architecture:** `knowledge_note.title` 컬럼(V17) + 엔티티 필드. 서비스 `normalize` 가 다른 텍스트 필드처럼 strip → 빈 값 null 처리. `KnowledgeNoteSpecs.matchesTerm` 의 OR 에 title LIKE 추가.

**Tech Stack:** Spring Boot 4.1, Java 25, JPA, Flyway(MySQL), JUnit5/Mockito/MockMvc.

**Spec:** `docs/superpowers/specs/2026-11-20-knowledge-note-title-design.md`
**짝 계획:** `FE-HCM/docs/superpowers/plans/2026-11-20-knowledge-note-title-fe.md` (이 계획의 API 가 먼저 배포돼야 함)

## Global Constraints

- 제목은 선택. 최대 100자(`KnowledgeNote.MAX_TITLE_LENGTH = 100`). 초과 메시지: `"제목은 100자 이하로 입력해주세요."`
- "내용 또는 이미지 중 하나는 필수" 규칙은 그대로 — 제목만 있는 메모는 `KNOWLEDGE_NOTE_CONTENT_OR_IMAGE_REQUIRED`.
- 기존 메모는 title NULL(백필 없음).
- 마이그레이션은 DDL 한 개, `ALGORITHM = INPLACE, LOCK = NONE`, 이미 적용된 파일은 수정 금지(CLAUDE.md).
- 시작 전 BE 저장소에 GitHub 이슈를 만들고, 브랜치 `feature/issue-187`, 커밋 메시지는 한국어 + 끝에 `(#187)`. PR base 는 `main`. 커밋 전 `./gradlew build`.

## Review Focus

1. 공백·전각 공백(U+3000)만 있는 제목 → null 로 저장(빈 문자열 저장 금지). → Task 1 테스트
2. 수정 시 제목을 비워 보내면 기존 제목이 지워져야 한다(유지가 아님 — visibility 와 다름). → Task 1 테스트
3. 101자 제목은 서비스에 닿기 전 400. → Task 1 컨트롤러 테스트
4. 제목에만 검색어가 있는 메모가 검색되고, `%`/`_` 가 리터럴로 취급된다(기존 escape 경로 공유). → Task 2 테스트
5. 제목만 있고 내용·이미지가 없으면 거부. → Task 1 테스트

---

### Task 1: 제목 저장·수정·응답

**Files:**
- Create: `src/main/resources/db/migration/V17__add_knowledge_note_title.sql`
- Modify: `src/main/java/com/behcm/domain/knowledge/entity/KnowledgeNote.java`
- Modify: `src/main/java/com/behcm/domain/knowledge/dto/KnowledgeNoteRequest.java`
- Modify: `src/main/java/com/behcm/domain/knowledge/dto/KnowledgeNoteResponse.java`
- Modify: `src/main/java/com/behcm/domain/knowledge/service/KnowledgeNoteService.java` (`create`, `update`, `normalize`, `NormalizedNote`)
- Test: `src/test/java/com/behcm/domain/knowledge/service/KnowledgeNoteServiceTest.java`
- Test: `src/test/java/com/behcm/domain/knowledge/controller/KnowledgeNoteControllerTest.java`

**Interfaces:**
- Produces: `KnowledgeNote.getTitle(): String`, 빌더 `.title(String)`, `update(KnowledgeCategory, String title, String content, String sourceUrl, String sourceText, String stockSymbol, String stockName, KnowledgeVisibility)` — title 은 category 다음 인자. `KnowledgeNoteResponse.title()` (JSON `title`, category 다음 위치). `KnowledgeNoteRequest.title`.

- [ ] **Step 1: 이슈·브랜치 생성** — 이슈 번호 N 확인 후 `git checkout -b feature/issue-187`

- [ ] **Step 2: 실패 테스트 작성 (서비스)** — `request(...)` 헬퍼 재사용

```java
@Test
@DisplayName("제목은 앞뒤 공백·전각 공백을 지워 저장한다")
void create_title_isStripped() {
    KnowledgeNoteRequest r = request(KnowledgeCategory.DEV, null, null);
    r.setTitle("  HBM 증설　");
    assertThat(saveAndCapture(r).getTitle()).isEqualTo("HBM 증설");
}

@Test
@DisplayName("공백만 있는 제목은 null 로 저장한다")
void create_blankTitle_storesNull()  // r.setTitle("　 ") → getTitle() isNull

@Test
@DisplayName("제목만 있고 내용·이미지가 없으면 거부한다")
void create_titleOnly_throwsContentOrImageRequired()
// r.setTitle("제목"); r.setContent(" ") → assertCode(..., KNOWLEDGE_NOTE_CONTENT_OR_IMAGE_REQUIRED)

@Test
@DisplayName("수정에서 제목을 비우면 기존 제목이 지워진다")
void update_blankTitle_clearsExistingTitle()
// 기존 note(title "옛 제목") 를 findEditable 경로로 반환하도록 기존 update 테스트와 같은 방식으로 stub,
// r.setTitle(null) 로 update → note.getTitle() isNull, response.title() isNull
```

- [ ] **Step 3: 실패 테스트 작성 (컨트롤러)**

```java
@Test
@DisplayName("create는 제목이 100자를 넘으면 400을 반환한다")
void create_withTooLongTitle_returns400()
// create_withTooLongContent_returns400 와 같은 형태, setContent("내용"), setTitle("가".repeat(101)),
// status 400 + verifyNoInteractions(knowledgeNoteService)
```

- [ ] **Step 4: 실패 확인** — `./gradlew test --tests "*KnowledgeNoteServiceTest" --tests "*KnowledgeNoteControllerTest"` → 컴파일 실패(`setTitle`/`getTitle` 없음)

- [ ] **Step 5: 마이그레이션**

```sql
-- knowledge_note.title : 메모 제목(선택). 목록에서 메모를 알아보기 위한 한 줄.
-- 기존 메모는 NULL 로 둔다 — 목록은 제목이 없으면 내용 첫 줄을 대신 보여준다.
ALTER TABLE `knowledge_note` ADD COLUMN `title` varchar(100) NULL, ALGORITHM = INPLACE, LOCK = NONE;
```

- [ ] **Step 6: 구현**
  - 엔티티: `MAX_TITLE_LENGTH = 100`, `@Column(length = MAX_TITLE_LENGTH) private String title;`, 빌더·`update` 에 추가(시그니처는 Interfaces 참고).
  - Request: `@Size(max = 100, message = "제목은 100자 이하로 입력해주세요.") private String title;`
  - Response: `String title` 를 category 다음에, `of` 에서 `note.getTitle()`.
  - 서비스: `normalize` 에서 `blankToNull(request.getTitle())`, `NormalizedNote` 에 `title` 추가, `create` 빌더와 `update` 호출에 전달. 제목은 content/image 필수 판정에 영향 없음.
  - 기존 테스트·다른 호출부에서 `update(...)` 시그니처가 깨지면 함께 고친다(`grep -rn "\.update(" src | grep -i knowledge`).

- [ ] **Step 7: 통과 확인** — Step 4 명령 → PASS

- [ ] **Step 8: 커밋**

```bash
git add src/main/resources/db/migration/V17__add_knowledge_note_title.sql src/main/java/com/behcm/domain/knowledge src/test/java/com/behcm/domain/knowledge
git commit -m "feat: 지식 메모에 선택 입력 제목 추가 (#187)"
```

---

### Task 2: 제목을 검색 대상에 포함

**Files:**
- Modify: `src/main/java/com/behcm/domain/knowledge/repository/KnowledgeNoteSpecs.java` (`matchesTerm`, `matches` Javadoc)
- Test: `src/test/java/com/behcm/domain/knowledge/repository/KnowledgeNoteRepositoryTest.java`

**Interfaces:**
- Consumes: Task 1 의 `KnowledgeNote.builder().title(...)`.

- [ ] **Step 1: 실패 테스트 작성** — 제목 있는 메모 저장 헬퍼는 테스트 안에서 빌더로 직접 만든다

```java
@Test
void search_matchesTitle() {
    KnowledgeNote titled = repository.save(KnowledgeNote.builder()
            .member(me).category(KnowledgeCategory.DEV).title("HBM 증설 메모").content("본문").build());
    saveNote(me, KnowledgeCategory.DEV, "다른 본문", null, null, null);
    em.flush();
    em.clear();

    assertThat(search(me, null, null, "HBM")).extracting(KnowledgeNote::getId)
            .containsExactly(titled.getId());
}

@Test
void search_titleTreatsPercentLiterally()
// title "수익률 10% 목표" 인 메모와 title "수익률 100 목표" 인 메모 → search "10%" 는 앞의 것만
```

- [ ] **Step 2: 실패 확인** — `./gradlew test --tests "*KnowledgeNoteRepositoryTest"` → `search_matchesTitle` FAIL(빈 결과)

- [ ] **Step 3: 구현** — `matchesTerm` 의 `cb.or(...)` 맨 앞에 `cb.like(root.get("title"), pattern, ESCAPE)` 추가, `matches` Javadoc 의 "(내용/출처/종목/태그)" 를 "(제목/내용/출처/종목/태그)" 로.

- [ ] **Step 4: 전체 검증** — `./gradlew build` → BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/behcm/domain/knowledge/repository/KnowledgeNoteSpecs.java src/test/java/com/behcm/domain/knowledge/repository/KnowledgeNoteRepositoryTest.java docs/superpowers
git commit -m "feat: 지식 메모 검색에 제목 포함 (#187)"
```
