# 새 지식 메모 푸시 알림 (BE) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 공개 지식 메모가 생성되면 조회 권한자에게 푸시를 보내고, 모든 알림 발송을 "커밋 후 · 비동기 · 실패 격리"로 바꾼다.

**Architecture:** `NotificationFacade` 는 공개 시그니처를 유지한 채 id·문자열만 꺼내 커밋 뒤(트랜잭션이 없으면 즉시)
새 `NotificationSender` 의 비동기 메서드에 작업을 넘긴다. `NotificationSender` 는 트랜잭션 없이 대상·토큰을 조회하고
`FcmService` 로 보내며 모든 예외를 로그로 끝낸다. `KnowledgeNoteService.create` 는 PUBLIC 메모일 때 Facade 를 부른다.

**Tech Stack:** Spring Boot 4.1, Java 25, Spring Data JPA, Firebase Admin SDK, JUnit 5 + Mockito + AssertJ

**Spec:** `BE-HCM/docs/superpowers/specs/2026-11-26-knowledge-note-push-design.md`

## Global Constraints

- 알림 쪽 어떤 실패도 호출자 트랜잭션을 롤백시키거나 호출자에게 예외를 던지면 안 된다(`afterCommit` 포함).
- 기존 호출부 7곳(`ChatService`, `PenaltyService`, `RestService`, `WorkoutCommentService`, `WorkoutReminderService`, `WorkoutService` ×2)은 수정하지 않는다.
- 비동기 스레드로는 엔티티를 넘기지 않는다. id·문자열만 넘긴다.
- `NotificationSender` 에는 `@Transactional` 을 붙이지 않는다(죽은 토큰 `deleteByToken` 이 flush 되어야 한다).
- 새 메모 알림: type `KNOWLEDGE_NOTE_CREATED`, path `/notes`, 제목 `📝 {닉네임}님이 새 메모를 올렸어요!`, 본문 `[{종목명}] {헤드라인}`(종목 없으면 접두 없음), 본문 50자 초과 시 `50자 + "..."`.
- 헤드라인: 제목(trim) → 내용 중 ```` ``` ```` 로 시작하지 않는 첫 비어 있지 않은 줄 → `이미지 메모`.
- 대상: `knowledge_access.can_read = true` 회원 ∪ `role = ADMIN` 회원, 중복 제거, 작성자 제외. PUBLIC 생성만. 수정은 보내지 않는다.
- 컨텍스트가 필요한 테스트는 `IntegrationTestSupport` 상속, 하위 클래스에 `@MockitoBean` 금지.
- 명령은 `BE-HCM` 디렉터리에서 실행한다.

## Review Focus

- `afterCommit` 안에서 실행기가 작업을 거부(`TaskRejectedException`)해도 요청은 정상 응답해야 한다 → Task 4 테스트 `afterCommit_submitFails_doesNotThrow`.
- ADMIN 이면서 `knowledge_access` 행도 있는 회원, 그리고 ADMIN 인 작성자 → 한 번만 받고, 작성자는 받지 않는다 → Task 3 테스트 `sendToKnowledgeReaders_dedupesAndExcludesAuthor`.
- 대상이 0명이거나 아무도 토큰이 없으면 FCM 을 호출하지 않고 예외도 없다 → Task 3 테스트 `sendToKnowledgeReaders_noTokens_doesNotSend`.
- `path` 가 `null` 이면 `putData` 가 NPE 를 낸다 → Task 1 테스트 `sendGroupNotification_nullPath_sendsEmptyPath`.
- 공개 범위를 안 보내면 PUBLIC 으로 저장되므로 알림이 가야 한다 → Task 5 테스트 `create_visibilityOmitted_notifies`.

---

### Task 0: 브랜치

- [ ] **Step 1:** 이슈를 만든 뒤 `git -C BE-HCM switch -c feature/issue-193` (N = 이슈 번호). 모든 커밋 메시지는 `(#193)` 로 끝맺는다.

### Task 1: 알림 전용 실행기 + FCM data 에 path 싣기

**Files:**
- Modify: `src/main/java/com/behcm/global/config/AsyncConfig.java`
- Modify: `src/main/java/com/behcm/domain/notification/service/FcmService.java`
- Test: `src/test/java/com/behcm/domain/notification/service/FcmServiceTest.java`

**Interfaces:**
- Produces: 빈 `notificationExecutor`(`Executor`), `AsyncConfig.getAsyncExecutor()` 가 `mailExecutor()` 반환.
  `FcmService.sendGroupNotification(...)` 는 data 에 `path`(null 이면 `""`)를 싣는다.

- [ ] **Step 1: 실패 테스트** — `FcmServiceTest` 에 기존 static mock 방식으로 전송된 `Message` 를 캡처하는 두 테스트:
  `sendGroupNotification_includesPath`(path `/notes` → data `path` = `/notes`),
  `sendGroupNotification_nullPath_sendsEmptyPath`(path `null` → data `path` = `""`, 예외 없음).
  `Message` 의 data 는 public getter 가 없으므로 `ReflectionTestUtils.getField(message, "data")` 로 읽는다.
- [ ] **Step 2:** `./gradlew test --tests "com.behcm.domain.notification.service.FcmServiceTest"` → 두 테스트 FAIL
- [ ] **Step 3: 구현**
  - `FcmService`: `.putData("path", path != null ? path : "")` 추가.
  - `AsyncConfig`: `@Bean(name = "notificationExecutor")` — core 2, max 4, queue 100, prefix `Notification-`,
    `MdcTaskDecorator`, 거부 핸들러는 예외 대신 `log.warn("Notification task dropped: executor saturated")`.
    `getAsyncExecutor()` 를 오버라이드해 `mailExecutor()` 를 반환하고, 주석에 "한정자 없는 @Async 의 기본값을 지금처럼 mailExecutor 로 고정"이라고 남긴다.
  - `TossApiExecutor` 클래스 주석의 "유일한 mailExecutor" 문장을 "AsyncConfig 가 기본값으로 고정한 mailExecutor" 로 고친다.
- [ ] **Step 4:** 같은 명령 → PASS
- [ ] **Step 5: 커밋** `feat: 알림 전용 실행기 추가, FCM data 에 path 포함 (#193)`

### Task 2: 대상·토큰 조회 쿼리

**Files:**
- Modify: `src/main/java/com/behcm/domain/notification/repository/FcmTokenRepository.java`
- Modify: `src/main/java/com/behcm/domain/knowledge/repository/KnowledgeAccessRepository.java`
- Modify: `src/main/java/com/behcm/domain/member/repository/MemberRepository.java`
- Test: `src/test/java/com/behcm/domain/knowledge/repository/KnowledgeAccessRepositoryTest.java`, `src/test/java/com/behcm/domain/notification/repository/FcmTokenRepositoryTest.java`(신규), `src/test/java/com/behcm/domain/member/repository/MemberRepositoryTest.java`(있으면 추가, 없으면 신규)

**Interfaces:**
- Produces:
  - `FcmTokenRepository.findTokensByMemberIds(Collection<Long> memberIds): List<String>` — `SELECT f.token FROM FcmToken f WHERE f.member.id IN :memberIds`
  - `KnowledgeAccessRepository.findReaderMemberIds(): List<Long>` — `SELECT ka.member.id FROM KnowledgeAccess ka WHERE ka.canRead = true`
  - `MemberRepository.findIdsByRole(MemberRole role): List<Long>` — `SELECT m.id FROM Member m WHERE m.role = :role`
  - `@Param` 은 `org.springframework.data.repository.query.Param` 을 쓴다(기존 `FcmTokenRepository` 의 lettuce import 는 이번에 spring 것으로 바꾼다).

- [ ] **Step 1: 실패 테스트**(기존 `KnowledgeAccessRepositoryTest` 처럼 `@Transactional` + `IntegrationTestSupport`)
  - `findReaderMemberIds_returnsOnlyCanRead`: (read,write)=(true,false),(false,true),(true,true) 세 회원 → 1·3번째 id 만.
  - `findTokensByMemberIds_returnsTokensOfGivenMembers`: 토큰 있는 회원 2명 + 다른 회원 1명 → 주어진 2명 토큰만.
  - `findIdsByRole_returnsAdminIds`: ADMIN 1명, USER 1명 저장 → 결과에 ADMIN id 포함, USER id 미포함.
- [ ] **Step 2:** `./gradlew test --tests "*KnowledgeAccessRepositoryTest" --tests "*FcmTokenRepositoryTest" --tests "*MemberRepositoryTest"` → FAIL(컴파일 오류)
- [ ] **Step 3: 구현** — 위 시그니처 그대로.
- [ ] **Step 4:** 같은 명령 → PASS
- [ ] **Step 5: 커밋** `feat: 알림 대상·토큰 id 기반 조회 쿼리 추가 (#193)`

### Task 3: NotificationSender (비동기 · 트랜잭션 없음 · 실패 격리)

**Files:**
- Create: `src/main/java/com/behcm/domain/notification/service/NotificationSender.java`
- Modify: `src/main/java/com/behcm/domain/notification/service/FcmService.java` (`sendGroupNotification` 의 `@Async` 제거 — 이제 Sender 스레드에서 동기로 돈다)
- Test: `src/test/java/com/behcm/domain/notification/service/NotificationSenderTest.java`

**Interfaces:**
- Consumes: Task 2 쿼리, `FcmService.sendGroupNotification(Long senderId, List<String> tokens, String title, String body, String tag, String path)`
- Produces (모두 `@Async("notificationExecutor")`, `void`, 예외를 밖으로 내지 않음):
  - `sendToMember(Long targetId, String title, String body, String type, String path)` — tag `type + "-" + targetId`, senderId 자리에 `targetId`(현행과 동일)
  - `sendToRoom(Long roomId, Long senderId, String title, String body, String type, String path)` — tag `type + "-" + roomId`, 방이 없으면 `log.warn` 후 종료
  - `sendToKnowledgeReaders(Long authorId, String title, String body, String type, String path)` — tag `type + "-" + authorId`
  - 본문은 여기서 50자 절단(`truncateMessage` 를 Facade 에서 옮김). 토큰이 비면 FCM 을 부르지 않는다.

- [ ] **Step 1: 실패 테스트**(Mockito 단위, `@Async` 는 단위 테스트에선 동기 호출)
  - `sendToMember_sendsToTargetTokens`: `findTokensByMemberIds(List.of(1L))` → `["t"]` → `sendGroupNotification(1L, ["t"], "title", "body", "PENALTY_ASSIGNED-1", "/path")`
  - `sendToMember_longBody_truncatesTo50Chars`: `"가"×60` → `"가"×50 + "..."`
  - `sendToRoom_excludesSender`: 방 멤버 1·2, sender 1 → `findTokensByMemberIds([2])`, tag `CHAT-{roomId}`
  - `sendToRoom_roomNotFound_doesNotThrowOrSend`: `findByIdAndIsActiveTrue` empty → 예외 없음, `fcmService` 무호출
  - `sendToKnowledgeReaders_dedupesAndExcludesAuthor`: readers `[1,2,3]`, admins `[3,4]`, author `3` → `findTokensByMemberIds` 인자가 `{1,2,4}`(순서 무관, 각 1번)
  - `sendToKnowledgeReaders_noTokens_doesNotSend`: 토큰 `[]` → `fcmService` 무호출
  - `send_repositoryThrows_doesNotPropagate`: `findReaderMemberIds` 가 `RuntimeException` → `assertThatCode(...).doesNotThrowAnyException()`
  - `noTransactionalAnnotation`: `NotificationSender.class` 와 그 public 메서드에 `@Transactional` 이 없음을 리플렉션으로 확인(Global Constraints 고정)
- [ ] **Step 2:** `./gradlew test --tests "*NotificationSenderTest"` → FAIL
- [ ] **Step 3: 구현** — 방 멤버는 기존 `findByWorkoutRoomOrderByJoinedAtFetchMember` 결과에서 id 만 뽑는다. 각 메서드 전체를 `try/catch (Exception e)` 로 감싸 `log.warn("Notification send failed (type={}, ...)", ..., e)`.
- [ ] **Step 4:** 같은 명령 + `--tests "*FcmServiceTest"` → PASS
- [ ] **Step 5: 커밋** `feat: 비동기 알림 발송기 NotificationSender 추가 (#193)`

### Task 4: NotificationFacade 를 "커밋 후 예약"으로 전환 + 지식 메모 진입점

**Files:**
- Modify: `src/main/java/com/behcm/domain/notification/service/NotificationFacade.java`
- Test: `src/test/java/com/behcm/domain/notification/service/NotificationFacadeTest.java` (기존 테스트를 Sender 위임 검증으로 교체. `notifyRoomMembers_roomNotFound_throws...` 는 삭제 — 이제 Sender 책임이고 예외를 던지지 않는다)

**Interfaces:**
- Consumes: Task 3 `NotificationSender`
- Produces (기존 시그니처 유지 + 신규 1개):
  - `notifyMember(Member target, String title, String body, String type, String path)`
  - `notifyRoomMembers(Long roomId, Member sender, String title, String body, String type, String path)`
  - `notifyKnowledgeReaders(Member author, String title, String body, String type, String path)` — 신규
  - `registerFcmToken(Member, String)` — 변경 없음
  - Facade 는 이제 `NotificationSender` 와 `FcmService`(토큰 등록용)만 의존한다. 클래스 레벨 `@Transactional` 은 `registerFcmToken` 메서드로 옮긴다.

- [ ] **Step 1: 실패 테스트**(각 테스트 `@AfterEach` 에서 동기화가 활성이면 `clearSynchronization()` — `KnowledgeNoteImageCleanerTest` 방식)
  - `noTransaction_sendsImmediately`: 동기화 비활성 → `notifyMember(target(1L), ...)` 직후 `sender.sendToMember(1L, ...)` 호출
  - `inTransaction_defersUntilAfterCommit`: `initSynchronization()` → 호출 직후 `sender` 무호출 → 등록된 synchronization 의 `afterCommit()` 실행 → `sendToRoom(roomId, 1L, ...)` 호출
  - `rollback_doesNotSend`: `initSynchronization()` → 호출 → `afterCompletion(STATUS_ROLLED_BACK)` 만 실행 → `sender` 무호출
  - `afterCommit_submitFails_doesNotThrow`: `sender.sendToKnowledgeReaders` 가 `TaskRejectedException` → `afterCommit()` 이 예외를 던지지 않음
  - `notifyKnowledgeReaders_passesAuthorId`: author id 7 → `sendToKnowledgeReaders(7L, title, body, type, path)`
  - 기존 `registerFcmToken_delegatesToFcmService` 유지
- [ ] **Step 2:** `./gradlew test --tests "*NotificationFacadeTest"` → FAIL
- [ ] **Step 3: 구현** — 공개 메서드는 id 를 지역 변수로 꺼낸 뒤 `private void runAfterCommit(Runnable task)` 에 넘긴다. `runAfterCommit` 은 `KnowledgeNoteImageCleaner.deleteAfterCommit` 과 같은 분기이고, `task` 실행을 `try/catch (Exception e)` 로 감싸 `log.warn` 한다.
- [ ] **Step 4:** `./gradlew test --tests "com.behcm.domain.notification.*"` → PASS
- [ ] **Step 5: 커밋** `refactor: 알림 발송을 커밋 후 비동기로 실행하고 실패를 격리 (#193)`

### Task 5: 공개 메모 생성 시 알림

**Files:**
- Modify: `src/main/java/com/behcm/domain/knowledge/service/KnowledgeNoteService.java`
- Create: `src/main/java/com/behcm/domain/knowledge/service/KnowledgeNoteNotification.java` — 알림 문구 조립(순수 함수)
- Test: `src/test/java/com/behcm/domain/knowledge/service/KnowledgeNoteNotificationTest.java`, `KnowledgeNoteServiceTest.java`

**Interfaces:**
- Consumes: Task 4 `NotificationFacade.notifyKnowledgeReaders(...)`
- Produces:
  - `KnowledgeNoteNotification.TYPE = "KNOWLEDGE_NOTE_CREATED"`, `PATH = "/notes"`
  - `static String title(String nickname)` → `📝 {nickname}님이 새 메모를 올렸어요!`
  - `static String body(KnowledgeNote note)` → `[{stockName}] {headline}` / `{headline}`

- [ ] **Step 1: 실패 테스트**
  - `KnowledgeNoteNotificationTest`:
    - `body_titleAndStock`: title `"실적 메모"`, stockName `"삼성전자"` → `"[삼성전자] 실적 메모"`
    - `body_noTitle_usesFirstContentLineSkippingFences`: content `"\n```sql\nSELECT 1\n```"` → `"SELECT 1"`
    - `body_imageOnly`: title·content 없음 → `"이미지 메모"`
    - `title_format`: `"철수"` → `"📝 철수님이 새 메모를 올렸어요!"`
  - `KnowledgeNoteServiceTest`(`@Mock NotificationFacade notificationFacade` 추가):
    - `create_public_notifiesReaders`: PUBLIC → `notifyKnowledgeReaders(member, title("n"), body(saved), "KNOWLEDGE_NOTE_CREATED", "/notes")`
    - `create_visibilityOmitted_notifies`: visibility null → 호출됨
    - `create_private_doesNotNotify`: PRIVATE → `verifyNoInteractions(notificationFacade)`
    - `update_doesNotNotify`: 기존 update 성공 케이스에 `verifyNoInteractions(notificationFacade)` 추가
- [ ] **Step 2:** `./gradlew test --tests "com.behcm.domain.knowledge.service.*"` → FAIL
- [ ] **Step 3: 구현** — `create` 에서 `save` 뒤, 응답 생성 전에 `saved.getVisibility() == PUBLIC` 이면 호출.
- [ ] **Step 4:** 같은 명령 → PASS
- [ ] **Step 5: 커밋** `feat: 공개 지식 메모 생성 시 조회 권한자에게 푸시 (#193)`

### Task 6: 문서 + 전체 검증

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1:** `CLAUDE.md` 아키텍처에 `domain/notification` 항목 추가: "알림은 `NotificationFacade` 로만 보낸다. Facade 는 커밋 후(트랜잭션 없으면 즉시) `NotificationSender` 의 `notificationExecutor` 비동기 작업으로 넘기고, 알림 실패는 본 작업에 영향을 주지 않는다. 비동기 쪽에 엔티티를 넘기지 말고 `@Transactional` 을 붙이지 말 것(`deleteByToken` flush)." `domain/knowledge` 항목에 "PUBLIC 메모 생성 시 조회 권한자+ADMIN(작성자 제외)에게 푸시" 한 줄.
- [ ] **Step 2:** `./gradlew build` → BUILD SUCCESSFUL
- [ ] **Step 3: 커밋** `docs: 알림 발송 규칙 문서화 (#193)`
