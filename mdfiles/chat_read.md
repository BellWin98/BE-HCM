## 채팅 읽음/안읽음 표시를 위한 백엔드 변경 사항 정리

프론트엔드에서는 `ChatMessage` 타입에 `unreadCount?: number` 필드를 추가했고, 각 메시지에 대해 **아직 읽지 않은 인원 수**를 이 필드로 표시합니다.  
백엔드는 아래 요구사항을 만족하도록 구현/수정이 필요합니다.

---

## 1. 공통 개념 및 요구사항

- **대상 도메인**
  - 운동방(WorkoutRoom)의 채팅 메시지.
  - 각 메시지별로 **현재 시점에 이 메시지를 아직 읽지 않은 방 멤버 수**를 계산해야 함.

- **기본 전제**
  - 각 방 멤버에 대해 `lastReadMessageId` 또는 이와 동일한 의미의 정보(마지막으로 읽은 메시지 기준)를 저장하는 구조가 필요.
  - 메시지 `id`는 순서를 보장할 수 있어야 하며(자동 증가 PK 또는 생성 시점 기준),  
    `member.lastReadMessageId >= message.id` 인 경우, 해당 멤버는 그 메시지를 읽은 것으로 간주할 수 있도록 설계.

- **표시 규칙(프론트 기준)**
  - 모든 메시지(내/다른 사람 것 모두)에 `unreadCount > 0`이면 숫자를 보여주고,
  - `unreadCount === 0` 또는 `null/undefined`면 아무 숫자도 표시하지 않음.

---

## 2. 데이터 모델/엔티티 설계

### 2.1. 메시지 엔티티 예시

- 메시지 자체에는 **unreadCount를 저장하지 않고**, 조회 시점에 계산하는 것을 권장.
- 예시 필드 (이미 존재한다고 가정):
  - `id: Long` (PK, 순서 보장)
  - `roomId: Long`
  - `senderNickname: String`
  - `content: String`
  - `type: TEXT | IMAGE | SYSTEM ...`
  - `imageUrl: String?`
  - `createdAt: LocalDateTime`

### 2.2. 방 멤버의 읽음 포인터 필드 추가

운동방 멤버 테이블(예: `WorkoutRoomMember`)에 아래 필드를 추가하는 것을 권장합니다.

- `lastReadMessageId: Long?`
  - 해당 멤버가 **마지막으로 읽은 메시지의 id**.
  - 채팅방 입장 후 `/chat/rooms/{roomId}/read` API 호출 시, 서버에서 이 값 업데이트.
  - 아직 어떤 메시지도 읽지 않은 경우 `null`.

> 구현 방식은 `lastReadAt` 시간 기반도 가능하지만,  
> 프론트에서 메시지 id 기준으로 동작할 것을 고려하면 `lastReadMessageId`가 더 다루기 쉽습니다.

---

## 3. REST API 변경: GET /chat/rooms/{roomId}/messages

프론트엔드가 사용하는 API는 다음과 같습니다.

- `GET /chat/rooms/{roomId}/messages?cursorId={cursorId}&size=20`
  - 응답 타입: `ChatHistoryResponse`
  - `ChatHistoryResponse.messages` 의 각 원소가 `ChatMessage` 입니다.

### 3.1. ChatMessage 응답 스키마에 unreadCount 추가

- 현재 프론트엔드 타입 (`src/types/index.ts`) 기준:

```ts
export interface ChatMessage {
  id: string;
  sender: string;
  content: string;
  timestamp: string;
  type: "TEXT" | "IMAGE";
  imageUrl?: string;
  unreadCount?: number;
}
```

백엔드는 위 스키마에 맞게, 각 메시지에 `unreadCount` 값을 채워서 반환해야 합니다.

### 3.2. unreadCount 계산 방식 (권장 로직)

1. **해당 방의 전체 활성 멤버 목록 조회**
   - 예: `List<WorkoutRoomMember> members = findMembersByRoomId(roomId)`
   - 탈퇴/강퇴된 멤버는 제외.

2. **각 멤버의 lastReadMessageId 조회**
   - `member.lastReadMessageId`가 `null`이면, 아직 아무 메시지도 읽지 않은 것으로 간주.

3. **메시지 리스트를 조회한 뒤, 각 메시지에 대해 unreadCount 계산**
   - 특정 메시지 `m`에 대해, 아래 조건을 만족하는 멤버 수를 셉니다.
     - `member.lastReadMessageId == null` 또는
     - `member.lastReadMessageId < m.id`

   - pseudo code:

```kotlin
val members = memberRepository.findByRoomId(roomId) // 활성 멤버
val lastReadMap = members.associateBy({ it.id }, { it.lastReadMessageId })

messages.map { m ->
    val unread = members.count { member ->
        val lastReadId = lastReadMap[member.id]
        lastReadId == null || lastReadId < m.id
    }

    ChatMessageResponse(
        id = m.id,
        sender = m.senderNickname,
        content = m.content,
        timestamp = m.createdAt,
        type = m.type,
        imageUrl = m.imageUrl,
        unreadCount = unread
    )
}
```

4. **커서 기반 페이지네이션 고려**
   - `cursorId`가 있을 때는, `id < cursorId` 인 메시지들 중 최근 20개를 가져오는 식으로 구현되어 있을 가능성이 높습니다.
   - `unreadCount` 계산은 **조회된 메시지 목록 전체**에 대해 위 로직을 그대로 적용하면 됩니다.

---

## 4. 읽음 처리 API: POST /chat/rooms/{roomId}/read

프론트에서는 아래 함수를 사용합니다.

```ts
async updateLastRead(roomId: number): Promise<void> {
  return this.request(`/chat/rooms/${roomId}/read`, { method: "POST" });
}
```

이 API는 “현재 유저가 이 방의 최근 메시지까지 읽었다”는 의미로 호출됩니다.

### 4.1. 처리 로직

1. **인증된 사용자/멤버 식별**
   - AccessToken에서 memberId를 추출.
   - `WorkoutRoomMember` 엔티티 조회 (`roomId`, `memberId` 기준).

2. **해당 방의 가장 최신 메시지 id 조회**
   - 예: `Long latestMessageId = messageRepository.findLatestIdByRoomId(roomId)`
   - 메시지가 하나도 없는 방이라면, `latestMessageId`는 `null`일 수 있음.

3. **멤버의 lastReadMessageId 업데이트**
   - `latestMessageId`가 `null`인 경우: 별도 업데이트 필요 없거나, `lastReadMessageId`를 `null`로 유지.
   - `latestMessageId`가 존재하는 경우:
     - `member.lastReadMessageId = max(member.lastReadMessageId, latestMessageId)` 처럼 **뒤로 되돌아가지 않도록** 갱신.

4. **트랜잭션 내에서 저장**
   - `memberRepository.save(member)` 또는 JPA 영속성 컨텍스트에서 flush.

5. **응답**
   - 단순 200 OK / 204 No Content 정도로 충분.

---

## 5. STOMP 브로드캐스트 설계 (선택 사항이지만 강력 추천)

현재 프론트는 STOMP로 채팅 메시지를 구독하고 있습니다.

- 구독 경로:
  - `/topic/chat/room/{roomId}`

- 메시지 수신 핸들러:
  - `ChatRoom.tsx`의 `onMessageReceived`에서 `message.body`를 JSON 파싱 후, `ChatMessage`로 간주해 append.

### 5.1. 채팅 메시지 STOMP 페이로드에 unreadCount 포함

사용자가 메시지를 보낼 때, 서버는 기존처럼 해당 방 topic으로 메시지를 브로드캐스트합니다.  
이때도 REST와 동일하게 `unreadCount`를 포함한 형태로 내려줘야 합니다.

예시 payload:

```json
{
  "id": 123,
  "sender": "홍길동",
  "content": "안녕하세요",
  "timestamp": "2025-03-13T10:00:00Z",
  "type": "TEXT",
  "imageUrl": null,
  "unreadCount": 3
}
```

프론트는 이 값을 그대로 UI에 표시합니다.

### 5.2. 읽음 상태 변경 브로드캐스트 (READ_STATUS 타입) – 선택

사용자가 `/chat/rooms/{roomId}/read`를 호출하면, 다른 클라이언트(특히 메시지 보낸 사람들)의  
`unreadCount`도 줄어들어야 합니다. 이를 실시간으로 반영하려면, STOMP를 통해 읽음 상태를 브로드캐스트하는 것이 좋습니다.

**권장 포맷:**

- 동일 topic(`/topic/chat/room/{roomId}`)으로 아래 형태의 메시지 발행:

```json
{
  "type": "READ_STATUS",
  "updatedMessages": [
    { "messageId": 120, "unreadCount": 0 },
    { "messageId": 121, "unreadCount": 1 },
    { "messageId": 122, "unreadCount": 2 }
  ]
}
```

- 프론트 처리 방식(요약):
  - `onMessageReceived`에서 `body.type`을 확인.
  - `type === 'READ_STATUS'`인 경우:
    - `updatedMessages` 를 기반으로 현재 `messages` state 안에서 id가 일치하는 메시지의 `unreadCount`만 업데이트.
  - 그 외(`TEXT`, `IMAGE` 등)는 기존처럼 채팅 메시지로 append.

> READ_STATUS 브로드캐스트가 없다면, 각 사용자는 **자신의 클라이언트에서만** 최신 읽음 상태를 보게 되고,  
> 다른 사람의 읽음 반영은 새 채팅이 올 때까지 지연될 수 있습니다.  
> 실시간성이 중요하다면 반드시 구현하는 것을 추천합니다.

---

## 6. 성능 고려 사항

- **unreadCount 계산 비용**
  - 메시지 1개당 `O(멤버 수)` 연산이 필요합니다.
  - 방 인원이 극단적으로 많지 않다면(수십 명 수준), 문제되지 않을 가능성이 높습니다.
  - 더 최적화가 필요하다면:
    - 각 메시지별 `readCount`를 캐시/테이블로 관리하고, `unreadCount = totalMembers - readCount` 로 계산하는 구조도 고려 가능.

- **트랜잭션**
  - `/read` API 호출 시 멤버의 lastReadMessageId 업데이트는 **짧은 트랜잭션**으로 마무리해야 합니다.
  - STOMP 브로드캐스트는 트랜잭션 종료 직후(또는 비동기 이벤트 리스너)에서 처리하는 것이 좋습니다.

---

## 7. 체크리스트

1. [ ] `WorkoutRoomMember` (또는 대응 테이블)에 `lastReadMessageId` 필드 추가.
2. [ ] `/chat/rooms/{roomId}/messages` 응답 DTO에 `unreadCount` 필드 추가.
3. [ ] 메시지 조회 시, 방 멤버의 `lastReadMessageId` 기반으로 각 메시지의 `unreadCount` 계산 로직 구현.
4. [ ] STOMP 채팅 메시지 브로드캐스트 payload에 `unreadCount` 포함.
5. [ ] `POST /chat/rooms/{roomId}/read`:
   - [ ] 현재 멤버 조회 후, 해당 방의 최신 메시지 id 기준으로 `lastReadMessageId` 업데이트.
   - [ ] (선택) 읽음 상태 변경 내용을 `READ_STATUS` 타입 STOMP 메시지로 브로드캐스트.
6. [ ] 기본 시나리오 테스트:
   - [ ] A, B, C 3명이 같은 방에 접속.
   - [ ] A가 메시지 보냄 → 각 클라이언트 메시지에 `unreadCount = 2` 표시.
   - [ ] B가 채팅방을 열어 `/read` 호출 → A, C 화면에서 해당 메시지 `unreadCount = 1`로 갱신.
   - [ ] C도 `/read` → 모든 클라이언트에서 해당 메시지의 숫자가 사라짐(0 또는 미표시).
