# 새 지식 메모 푸시 알림 설계

## 왜

새 지식 메모가 올라와도 조회 권한자들이 모르고 지나친다. 올라온 순간 푸시로 알려서 메모 목록을 열게 한다.

알림은 부가 기능이다. **메모 저장(핵심 기능)은 알림이 어떤 이유로 실패해도 반드시 성공해야 하고, 알림이
가끔 유실되는 것은 괜찮다.**

## 목표 / 비목표

- 목표: 공개(PUBLIC) 메모가 새로 생성되면 조회 권한자 전원에게 푸시를 보낸다. 알림을 누르면 `/notes` 가 열린다.
- 목표: 알림 발송을 "커밋 후 · 비동기 · 실패 격리"로 바꿔, 기존 알림 7곳도 호출부 수정 없이 같은 보장을 받는다.
- 비목표: 메모 수정 알림, 비공개 메모 알림(관리자 대상 포함), 알림 묶기·요약, 회원별 알림 끄기 설정,
  발송 보장(아웃박스).
- 실패 조건: 알림 쪽 오류(대상 조회 실패, FCM 장애 등)로 메모 저장이나 기존 기능(운동 인증·채팅 등)이
  롤백되는 것. 비공개 메모의 제목·내용이 알림으로 새는 것.

## 접근 방식

| 안 | 내용 | 판단 |
|----|------|------|
| 1 | 서비스에서 `NotificationFacade` 직접 호출(현행) | 대상 조회가 호출자 트랜잭션 안에서 동기로 돌아, 예외 시 본 작업까지 롤백된다(예: `notifyRoomMembers` 의 `WORKOUT_ROOM_NOT_FOUND`). 롤백된 데이터에 대한 알림도 나갈 수 있다 |
| 2 | 도메인 이벤트 + `@TransactionalEventListener(AFTER_COMMIT)` | 보장은 충분하지만 기존 7곳의 호출부를 모두 바꿔야 하고, 트랜잭션 밖에서 발행한 이벤트는 기본값에서 조용히 버려진다(스케줄러 경로 위험) |
| **3** | **호출은 그대로, `NotificationFacade` 내부에서 커밋 후로 미루고 비동기로 실행** | **선택.** 같은 보장을 Facade 수정만으로 얻고, 기존 알림도 함께 고쳐진다. `KnowledgeNoteImageCleaner.deleteAfterCommit` 와 같은 패턴이다 |

검토 후 제외: 아웃박스+스케줄러(유실 허용이라 과함), 메시지 큐(인프라 추가, 서버 사양 제약), FCM 토픽
구독(권한·토큰 변경마다 구독 동기화가 필요하고 트랜잭션 문제는 해결하지 않음).

알림 빈도는 **메모마다 즉시 1건**(사용자 결정). 연속 업로드 시 여러 건이 가는 것은 감수한다.

## 설계

### 1. 알림 발송 구조 변경 (BE, `domain/notification`)

- `NotificationFacade` 의 공개 메서드(`notifyMember`, `notifyRoomMembers`, 신규 메서드)는 **호출 시점에
  필요한 값(id, 문자열)만 꺼내고** 실제 작업을 예약만 한다.
  - 트랜잭션 동기화가 활성이면 `afterCommit` 에 등록, 아니면 즉시 예약한다(`deleteAfterCommit` 과 동일한 분기).
  - 롤백되면 아무것도 보내지 않는다.
- 예약된 작업은 비동기 스레드에서 돈다: 대상 조회(읽기 전용 트랜잭션) → 토큰 조회 → FCM 전송.
  **모든 예외는 이 안에서 로그로 끝나고 호출자에게 전파되지 않는다.**
- 엔티티를 스레드 너머로 넘기지 않는다(지연 로딩 실패 방지). 넘기는 것은 id 와 문자열뿐이다.
- 기존 호출부 7곳(`ChatService`, `PenaltyService`, `RestService`, `WorkoutCommentService`,
  `WorkoutReminderService`, `WorkoutService` ×2)의 **시그니처와 호출 코드는 바꾸지 않는다.**
- 동작 변화: `notifyRoomMembers` 는 운동방이 없을 때 더 이상 예외를 던지지 않고 로그만 남긴다.
- `registerFcmToken` 은 알림 발송이 아니므로 지금처럼 동기로 둔다.

### 2. 알림 이동 경로 전달 (BE + FE)

- `FcmService.sendGroupNotification` 이 받는 `path` 를 FCM data 에 `path` 로 실어 보낸다(현재는 버려짐).
- `firebase-messaging-sw.js`
  - `onBackgroundMessage`: `showNotification` 옵션의 `data.url` 에 `path || "/"` 를 넣는다.
  - `notificationclick`: 열린 앱 창이 있으면 그 창을 포커스하고 해당 경로로 이동, 없으면 새 창을 연다.
- 기존 알림의 `path` 는 `""` 이므로 클릭 시 `/` — 지금과 같은 동작이다.

### 3. 새 메모 알림 (BE, `domain/knowledge`)

- 발송 조건: `KnowledgeNoteService.create` 에서 저장한 메모의 공개 범위가 **PUBLIC** 일 때만. 수정은 보내지 않는다.
- 대상: `knowledge_access.can_read = true` 인 회원 ∪ role 이 ADMIN 인 회원, 중복 제거, **작성자 제외**.
  `canWrite` 만 있는 회원은 다른 회원의 메모를 볼 수 없으므로 대상이 아니다.
  대상 조회 쿼리는 knowledge 도메인(리포지토리)에 두고, 비동기 작업 안에서 실행한다.
- 내용
  - type: `KNOWLEDGE_NOTE_CREATED`, path: `/notes`
  - 제목: `📝 {작성자 닉네임}님이 새 메모를 올렸어요!`
  - 본문: 종목이 있으면 `[{종목명}] ` 접두 + 메모 제목, 제목이 없으면 내용 앞부분. 길이는 기존 `truncateMessage`(50자).

### 4. 포그라운드 (FE)

- 앱이 열려 있을 때는 기존 `DashboardPage` 의 `onMessage` 토스트를 그대로 쓴다. `path` 가 있으면 토스트에
  "보기" 버튼을 붙여 해당 경로로 이동한다.

### 5. 구현 계획 작성 중 확인한 사항 (계획 단계 결정)

- **FCM 토큰 등록 위치:** 지금 토큰은 운동방에 참여한 회원이 대시보드나 채팅방에 들어갈 때만 등록된다.
  운동방에 참여하지 않은 조회 권한자는 토큰이 없어 알림을 받을 수 없으므로, `/notes` 화면에 들어올 때도
  `ensureFcmToken()` 으로 등록한다.
- **실행기:** 기본 실행기를 지정하지 않아, 한정자 없는 `@Async` FCM 전송이 지금은 유일한 `mailExecutor`
  (최대 5스레드, 큐 10)에서 돈다. 큐가 넘치면 `TaskRejectedException` 이 호출 스레드로 던져진다.
  알림 전용 `notificationExecutor` 를 추가한다. 빈이 하나 더 생기면 한정자 없는 `@Async`
  (`TossStockUniverseLoader`)의 기본값이 모호해지므로, `AsyncConfig.getAsyncExecutor()` 가 `mailExecutor`
  를 반환하도록 해서 지금 동작을 명시적으로 유지한다.
- **`afterCommit` 안의 예외:** `afterCommit` 에서 던진 예외는 커밋이 끝난 뒤 호출자에게 전파되어, 저장은
  됐는데 500 응답이 나간다. 그래서 작업 제출(실행기 거부 포함)을 `try/catch` 로 감싼다.
- **트랜잭션:** 비동기 발송 쪽은 트랜잭션을 열지 않는다. 대상·토큰 조회는 id·문자열만 돌려주는 쿼리로
  리포지토리 호출마다 끝나게 한다. 읽기 전용 트랜잭션으로 감싸면 `FcmTokenRepository.deleteByToken`
  (죽은 토큰 삭제)이 거기에 합류해 flush 되지 않는다.

## 테스트 (TDD)

- `NotificationFacade`: 트랜잭션 커밋 후에만 발송, 롤백 시 미발송, 트랜잭션 밖 호출 시 즉시 발송, 대상 조회
  예외가 호출자에게 전파되지 않음.
- 지식 메모: PUBLIC 생성 시 대상 = 조회 권한자 + ADMIN − 작성자, PRIVATE 생성·수정 시 미발송,
  알림 실패 시에도 메모 저장 성공.
- `FcmService`: data 에 `path` 포함.
- FE: 서비스워커 클릭 시 `data.url` 로 이동, 토스트 "보기" 버튼 이동.
- 스프링 컨텍스트 테스트는 `IntegrationTestSupport` 를 상속한다.

## 구현자 판단에 맡기는 것

- 비동기 실행기: 현재 `AsyncConfig` 에는 `mailExecutor` 하나뿐이고 기본 실행기를 지정하지 않아,
  기존 `@Async` FCM 전송이 어느 풀에서 도는지 확인이 필요하다. 알림 전용 실행기(`notificationExecutor`,
  `MdcTaskDecorator` 적용)를 두는 것을 권장한다.
- 비동기 작업을 담을 빈 구성(같은 빈 안에서 `@Async` 자기 호출이 프록시를 타지 않는 점 주의).
- 대상 조회 쿼리 형태(한 쿼리로 합칠지, 두 번 조회 후 합칠지).
- 작업 후 BE `CLAUDE.md` 의 notification/knowledge 설명에 "알림은 커밋 후 비동기, 실패 격리" 규칙을 추가한다.
