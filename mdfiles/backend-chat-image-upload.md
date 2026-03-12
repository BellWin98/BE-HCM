# 채팅 이미지 업로드 — 백엔드 작업 사항

프론트엔드 ChatRoom 이미지 업로드 기능에 맞추어 백엔드에서 구현해야 할 API 및 WebSocket 스펙을 정리한 문서입니다.

---

## 1. REST API: 채팅 이미지 업로드

### 1.1 엔드포인트

| 항목   | 내용                                       |
| ------ | ------------------------------------------ |
| Method | `POST`                                     |
| Path   | `/api/chat/rooms/{roomId}/images`          |
| 인증   | `Authorization: Bearer {accessToken}` 필요 |

### 1.2 요청

- **Content-Type**: `multipart/form-data`
- **Body**: form 필드명 `file` 로 이미지 파일 1개 전송

**프론트엔드 전송 예시**

```ts
const formData = new FormData();
formData.append("file", file); // File 객체 1개
await api.uploadChatImage(roomId, formData);
```

### 1.3 요청 검증 (권장)

프론트에서 아래와 같이 제한하고 있으나, 백엔드에서도 동일하게 검증하는 것을 권장합니다.

| 검증 항목      | 값                                      |
| -------------- | --------------------------------------- |
| 허용 MIME 타입 | `image/jpeg`, `image/png`, `image/webp` |
| 최대 파일 크기 | 5MB (5 × 1024 × 1024 bytes)             |

- 검증 실패 시: 400 등 적절한 HTTP 상태 코드와 `message` 등 에러 메시지 반환
- 프론트는 `error.response?.data?.message` 또는 `error.message`를 사용자에게 표시합니다.

### 1.4 응답

**성공 시 (예: 200)**

- 응답 body에 **이미지 URL**을 담아 반환해야 합니다.
- 프론트는 `response.data.data` 또는 `response.data`를 사용하므로, 아래 두 형태 모두 지원 가능합니다.

**형식 A (권장)**

```json
{
  "data": {
    "imageUrl": "https://your-cdn-or-storage.example/chat/rooms/1/abc123.jpg"
  }
}
```

**형식 B**

```json
{
  "imageUrl": "https://your-cdn-or-storage.example/chat/rooms/1/abc123.jpg"
}
```

- **필드명**: 반드시 `imageUrl` (카멜케이스).
- **값**: 클라이언트가 `<img src="">` 에 그대로 넣을 수 있는 **절대 URL** (공개 접근 가능한 URL).

### 1.5 백엔드 구현 시 할 일

- [ ] `POST /api/chat/rooms/{roomId}/images` 컨트롤러/라우트 추가
- [ ] `roomId`에 해당하는 채팅방 존재 여부 및 요청자가 해당 방 멤버인지 검증
- [ ] `multipart/form-data`에서 `file` 필드 수신 및 파일 타입/크기 검증 (jpeg, png, webp, 5MB 이하)
- [ ] 파일을 저장소(S3, 로컬 스토리지 등)에 업로드 후 **공개 URL** 생성
- [ ] 응답 body에 `imageUrl` 포함하여 반환 (위 1.4 형식 A 또는 B)

---

## 2. WebSocket(STOMP): 이미지 메시지 수신/저장/브로드캐스트

이미지 업로드 API로 `imageUrl`을 받은 뒤, 프론트는 **기존 채팅 메시지 전송 경로**로 이미지 메시지를 보냅니다.

### 2.1 전송 경로 및 페이로드

| 항목        | 내용                                                                          |
| ----------- | ----------------------------------------------------------------------------- |
| Destination | `/app/chat/room/{roomId}/send`                                                |
| Body (JSON) | `{ "type": "IMAGE", "content": "", "imageUrl": "{업로드 API에서 받은 URL}" }` |

**프론트엔드 전송 예시**

```ts
clientRef.current.publish({
  destination: `/app/chat/room/${roomId}/send`,
  body: JSON.stringify({
    type: "IMAGE",
    content: "",
    imageUrl: imageUrl,
  }),
  headers: {
    Authorization: `Bearer ${accessToken}`,
    "content-type": "application/json",
  },
});
```

### 2.2 백엔드 처리 요구사항

- 기존 텍스트 메시지(`type: "TEXT"`)와 동일한 엔드포인트에서 처리해도 됩니다.
- **type**이 `"IMAGE"`일 때:
  - **content**: 빈 문자열(`""`)로 수신됨.
  - **imageUrl**: 필수. 업로드 API에서 발급한 이미지 URL.
- 해당 메시지를 DB 등에 저장할 때 `type`, `imageUrl`(및 필요 시 `content`)를 저장하고, 동일 채팅방 구독자에게 그대로 브로드캐스트하면 됩니다.

### 2.3 채팅 기록 API 응답 형식

이미지 메시지도 **기존 채팅 기록 API** (`GET /api/chat/rooms/{roomId}/messages`) 응답에 포함되어야 합니다.

**메시지 객체 예시 (IMAGE)**

```json
{
  "id": "msg-uuid-or-id",
  "sender": "닉네임",
  "content": "",
  "timestamp": "2025-03-12T10:00:00.000Z",
  "type": "IMAGE",
  "imageUrl": "https://your-cdn.example/chat/rooms/1/abc123.jpg"
}
```

- `type`: `"TEXT"` | `"IMAGE"`
- 이미지 메시지일 때는 `imageUrl` 필드 필수, `content`는 빈 문자열 가능.

### 2.4 백엔드 구현 시 할 일

- [ ] `/app/chat/room/{roomId}/send` 에서 payload의 `type`이 `"IMAGE"`인 경우 처리
- [ ] `imageUrl` 검증(필수, 비어 있지 않은 URL 등) 후 DB/저장소에 메시지 저장
- [ ] 동일 방 구독자에게 `type`, `content`, `imageUrl` 포함한 메시지 브로드캐스트
- [ ] `GET /api/chat/rooms/{roomId}/messages` 응답에 이미지 메시지도 `type`, `imageUrl` 포함하여 반환

---

## 3. 정리

| 구분      | 항목               | 요약                                                                      |
| --------- | ------------------ | ------------------------------------------------------------------------- |
| REST      | 채팅 이미지 업로드 | `POST /api/chat/rooms/{roomId}/images`, multipart `file`, 응답 `imageUrl` |
| REST      | 검증               | 이미지 타입(jpeg/png/webp), 크기(5MB 이하), 방 존재·멤버 여부             |
| WebSocket | 이미지 메시지      | 기존 send 경로에서 `type: "IMAGE"`, `imageUrl` 수신·저장·브로드캐스트     |
| API       | 채팅 기록          | 이미지 메시지도 `type`, `imageUrl` 포함하여 반환                          |

위 스펙에 맞추어 구현하면 프론트엔드와 연동됩니다.
