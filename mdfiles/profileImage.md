# 백엔드 수정 요청: 프로필 사진 변경 기능

프론트엔드에서 **프로필 사진 변경** 기능이 구현되어 있습니다. 아래 스펙에 맞게 백엔드를 구현하거나 수정해 주세요.

---

## 1. 프론트엔드 동작 요약

1. 사용자가 마이페이지에서 아바타를 클릭해 이미지 파일을 선택한다.
2. **POST `/api/members/profile/image`** 로 이미지를 업로드하고, 응답으로 받은 `profileUrl`을 사용한다.
3. **PUT `/api/members/profile`** 에 `{ profileUrl }` 만 담아 요청해 프로필에 반영한다.
4. GET `/api/members/profile` 및 GET `/api/members/me` 응답에 `profileUrl`이 포함되어 있으면 헤더·마이페이지 등에서 프로필 사진으로 표시한다.

---

## 2. 반드시 구현할 API

### 2.1 프로필 이미지 업로드 (신규)

| 항목 | 내용 |
|------|------|
| **Method** | `POST` |
| **Path** | `/api/members/profile/image` (또는 백엔드 base path에 맞게 `members/profile/image`) |
| **인증** | Bearer 토큰 필수 (Authorization: Bearer {accessToken}) |
| **Content-Type** | `multipart/form-data` |
| **요청 필드** | 폼 필드 이름: **`image`** (파일 1개) |
| **허용 형식** | `image/jpeg`, `image/png`, `image/webp` (프론트에서 검증함. 백엔드에서도 검증 권장) |
| **용량 제한** | 최대 **5MB** (프론트에서 검증함. 백엔드에서도 제한 권장) |

**응답 (성공 시, 200):**

- 프론트는 `response.data.data` 또는 `response.data` 중 존재하는 값을 본문으로 사용한다.
- 본문에 **`profileUrl`** (string) 이 포함되어야 한다.

예시 (본문이 `data`로 감싸지는 경우):

```json
{
  "data": {
    "profileUrl": "https://your-cdn.com/profiles/abc123.jpg"
  }
}
```

예시 (본문이 바로 오는 경우):

```json
{
  "profileUrl": "https://your-cdn.com/profiles/abc123.jpg"
}
```

**에러 응답 (4xx/5xx):**

- HTTP body에 **`message`** (string) 필드가 있으면, 프론트는 이 값을 그대로 사용자에게 표시한다.

```json
{
  "message": "파일 형식이 올바르지 않습니다."
}
```

---

### 2.2 프로필 수정 (기존 API 확인용)

| 항목 | 내용 |
|------|------|
| **Method** | `PUT` |
| **Path** | `/api/members/profile` |
| **인증** | Bearer 토큰 필수 |
| **Content-Type** | `application/json` |
| **요청 body** | `{ "profileUrl"?: string, "nickname"?: string, "bio"?: string }` (일부만 보내도 됨) |

프로필 사진 변경 시 프론트는 **`{ "profileUrl": "업로드 API에서 받은 URL" }`** 만 보냅니다.

**응답 (성공 시, 200):**

- 갱신된 **회원 프로필 전체**를 반환해야 한다.
- 프론트 타입 `UserProfile` 에 맞추면 되며, 최소한 다음 필드가 있으면 된다:  
  `id`, `nickname`, `email`, `profileUrl`(선택), `bio`(선택), `totalWorkoutDays`, `currentStreak`, `longestStreak`, `totalPenalty`, `joinedAt`, `role`

---

### 2.3 프로필 조회 (기존 API 확인용)

- **GET `/api/members/profile`**
    - 응답에 **`profileUrl`** (string, nullable 또는 optional) 이 포함되어야 마이페이지에서 프로필 사진을 표시할 수 있다.

- **GET `/api/members/me`** (현재 로그인 사용자 정보)
    - 응답 타입이 `Member` 라고 가정하며, 여기에도 **`profileUrl`** 이 포함되어야 헤더 등에서 프로필 사진을 표시할 수 있다.

---

## 3. 체크리스트 (백엔드 구현 시 확인)

- [ ] **POST** `members/profile/image`
    - multipart 폼 필드명 **`image`**
    - 응답 본문에 **`profileUrl`** (문자열) 포함
    - 인증 필수, 5MB·이미지 타입 제한 권장
- [ ] **PUT** `members/profile`
    - body에 `profileUrl` (및 선택적으로 `nickname`, `bio`) 수신 가능
    - 응답에 갱신된 프로필 전체 반환
- [ ] **GET** `members/profile`
    - 응답에 `profileUrl` 필드 포함
- [ ] **GET** `members/me`
    - 응답에 `profileUrl` 필드 포함
- [ ] 에러 응답에 **`message`** 필드 포함 시, 프론트가 해당 문구를 그대로 사용자에게 노출

---

## 4. 참고: 프론트엔드 호출 순서

```
1. uploadProfileImage(file)  → POST /members/profile/image (multipart, field: "image")
2. updateUserProfile({ profileUrl })  → PUT /members/profile (JSON)
```
