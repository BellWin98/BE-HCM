## 관리자 운동방 채팅 내역 조회 API

관리자 화면(운동방 상세 → **채팅** 탭)에서 방 멤버들의 채팅 내역을 **읽기 전용**으로 조회하기 위한 API입니다.  
멤버용 `GET /chat/rooms/{roomId}/messages`와 **동일한 응답 스키마**를 사용하며, **ADMIN 역할**만 호출할 수 있습니다.

---

## 1. 엔드포인트

| 항목 | 내용 |
|------|------|
| Method / Path | `GET /api/admin/workout/rooms/{roomId}/messages` |
| Auth | `ADMIN` 역할 필수 (`Authorization: Bearer …`) |
| Path variable | `roomId` — 운동방 ID |
| Query | `cursorId` (optional), `size` (optional, default `20`, max 권장 `50`) |

### 1.1. Query 파라미터

- `size`: 한 번에 가져올 메시지 개수 (기본 20)
- `cursorId`: **더 오래된** 메시지 페이지 조회 시 사용. 멤버용 채팅 API와 동일한 커서 규칙을 따릅니다.
  - 최초 요청: `cursorId` 생략 → 최신 `size`건
  - 이전 대화: 응답의 `nextCursorId`를 다음 요청의 `cursorId`로 전달

### 1.2. 권한

- 요청자는 **방 멤버일 필요 없음** (관리자만 `ADMIN` 역할이면 됨).
- `roomId`에 해당하는 운동방이 존재하지 않으면 `404`.
- `ADMIN`이 아니면 `403`.

---

## 2. 응답 스키마

프론트엔드 타입 (`src/types/index.ts`) 기준:

```ts
export interface ChatMessage {
  id: string;
  sender: string;
  content: string;
  timestamp: string;
  type: 'TEXT' | 'IMAGE' | 'READ_STATUS' | 'SYSTEM';
  imageUrl?: string;
  unreadCount?: number;
}

export interface ChatHistoryResponse {
  messages: ChatMessage[];
  nextCursorId: number | null;
  hasNext: boolean;
}
```

### 2.1. 정렬 및 페이지네이션

- **멤버용** `GET /chat/rooms/{roomId}/messages`와 동일한 정렬·커서 동작을 권장합니다.
- 응답 `messages` 배열은 프론트에서 **오래된 순 → 최신 순**으로 표시하기 위해, 필요 시 클라이언트에서 재정렬하거나 API가 이미 시간순 오름차순이면 그대로 사용합니다.
- `hasNext: true`이고 `nextCursorId`가 있으면, 더 오래된 메시지가 존재함.

### 2.2. unreadCount (선택)

- 관리자 조회 화면에서는 `unreadCount`를 표시하지 않지만, 스키마 호환을 위해 멤버 API와 동일하게 내려도 무방합니다.

---

## 3. 구현 참고

- 메시지 저장소·조회 로직은 기존 채팅 도메인을 재사용하고, **인가(Authorization) 레이어만** `ADMIN` + `roomId` 검증으로 분리하는 방식이 적합합니다.
- `READ_STATUS`, `SYSTEM` 타입 메시지는 프론트에서 목록에서 제외합니다 (`isSystemChatType`).

---

## 4. 프론트엔드 연동

- API 클라이언트: `api.getAdminChatHistory(roomId, cursorId?, size?)`
- UI: `src/components/admin/AdminRoomChatTab.tsx` (읽기 전용, WebSocket·전송 없음)

---

## 5. 에러 코드 (권장)

| HTTP | 설명 |
|------|------|
| 200 | 성공 |
| 403 | ADMIN 아님 |
| 404 | `roomId` 없음 |
| 401 | 미인증 |
