# Admin API Contract (FE ↔ Backend)

백엔드에 Admin API가 아직 없으므로, 아래 스펙에 따라 구현해야 합니다.

**Base path:** `/api` (기존과 동일)  
**인증:** Bearer JWT  
**권한:** `ADMIN` role만 접근 가능 (서버에서 enforce 필요)  
**CORS:** `PATCH` 메서드 추가 필요 (역할 변경용)

---

## Members

| Method | Path                             | Description    |
| ------ | -------------------------------- | -------------- |
| GET    | `/admin/members`                 | 회원 목록/검색 |
| PATCH  | `/admin/members/{memberId}/role` | 회원 역할 변경 |

### GET /admin/members

**Query params:** `query`(검색어, 닉네임/이메일), `role`(USER|ADMIN|FAMILY), `page`, `size`  
**Response:** `PageResponse<Member>` (content, last, totalPages, number, size)

### PATCH /admin/members/{memberId}/role

**Body:** `{ "role": "USER" | "FAMILY" | "ADMIN" }`  
**Response:** `Member`

---

## Workout Rooms

| Method | Path                            | Description             |
| ------ | ------------------------------- | ----------------------- |
| GET    | `/admin/workout/rooms`          | 운동방 목록/검색 (전체) |
| GET    | `/admin/workout/rooms/{roomId}` | 운동방 상세 (규칙+멤버) |
| PUT    | `/admin/workout/rooms/{roomId}` | 운동방 규칙 수정        |

### GET /admin/workout/rooms

**Query params:** `query`(방 이름/방장), `active`(boolean), `page`, `size`  
**Response:** `PageResponse<WorkoutRoom>`

### GET /admin/workout/rooms/{roomId}

**Response:** `WorkoutRoomDetail` (workoutRoomInfo, workoutRoomMembers, currentMemberTodayWorkoutRecord)

- 기존 `WorkoutRoomDetailResponse`와 동일한 구조 (ADMIN은 참여 여부와 무관하게 조회)

### PUT /admin/workout/rooms/{roomId}

**Body:** `{ startDate, endDate?, maxMembers, minWeeklyWorkouts, penaltyPerMiss }`  
**Response:** `WorkoutRoom`

---

## 공통 응답 래퍼

프론트 `api.request`는 `response.data.data` 또는 `response.data`를 그대로 반환합니다.

- **성공 케이스(권장)**
  - 바디:
    - `{ "data": <실제_응답_객체>, "message": "성공 메시지", "success": true }`
  - 또는: `<실제_응답_객체>` (data 없이 바로)
- **에러 케이스**
  - HTTP status: 4xx / 5xx
  - 바디: `{ "message": "에러 메시지" }`
  - FE에서는 `error.response.data.message`를 사용해 토스트 노출

---

## 데이터 구조 정의 (백엔드 구현 기준)

### PageResponse<T>

```ts
interface PageResponse<T> {
  content: T[];
  last: boolean;
  totalPages: number;
  number: number; // 현재 페이지 (0-based)
  size: number; // 페이지 크기
}
```

### Member

```ts
interface Member {
  id: number;
  email: string;
  nickname: string;
  profileUrl: string;
  totalWorkoutDays: number;
  totalPenalty: number;
  createdAt: string; // ISO datetime
  role: "USER" | "ADMIN" | "FAMILY";
}
```

### WorkoutRoom

```ts
interface WorkoutRoom {
  id: number;
  name: string;
  minWeeklyWorkouts: number; // 주간 최소 운동 횟수
  penaltyPerMiss: number; // 1회 미달 벌금 (원)
  startDate: string; // 'yyyy-MM-dd'
  endDate: string | null; // 'yyyy-MM-dd' 또는 null
  maxMembers: number; // 최대 인원
  currentMembers: number; // 현재 인원
  ownerNickname: string; // 방장 닉네임
  isActive: boolean; // 현재 활성 여부
}
```

### WorkoutRoomDetail

```ts
interface WorkoutRoomDetail {
  workoutRoomInfo: WorkoutRoom | null;
  workoutRoomMembers: RoomMember[];
  currentMemberTodayWorkoutRecord: WorkoutRecord | null;
}

interface RoomMember {
  id: number;
  nickname: string;
  profileUrl: string;
  totalWorkouts: number; // 누적 운동 수
  weeklyWorkouts: number; // 이번 주 운동 수
  totalPenalty: number; // 누적 벌금
  isOnBreak: boolean; // 현재 휴식 중 여부
  joinedAt: string; // ISO datetime
  workoutRecords: WorkoutRecord[];
  restInfoList: RestInfo[];
}

interface WorkoutRecord {
  id: number;
  workoutDate: string; // 'yyyy-MM-dd'
  workoutTypes: string[]; // 프론트에서 enum 문자열 배열로 사용
  duration: number; // 분 단위
  imageUrls: string[]; // 인증 이미지 URL 목록
  createdAt: string; // ISO datetime
}

interface RestInfo {
  id: number;
  reason: string;
  startDate: string; // 'yyyy-MM-dd'
  endDate: string; // 'yyyy-MM-dd'
}
```

### AdminMemberListParams (쿼리)

```ts
interface AdminMemberListParams {
  query?: string; // 닉네임 or 이메일 (부분 일치)
  role?: "USER" | "ADMIN" | "FAMILY";
  page?: number; // 0-based
  size?: number; // 페이지 크기
}
```

### AdminWorkoutRoomListParams (쿼리)

```ts
interface AdminWorkoutRoomListParams {
  query?: string; // 방 이름 또는 방장 닉네임
  active?: boolean; // true: 활성, false: 비활성, 없으면 전체
  page?: number; // 0-based
  size?: number; // 페이지 크기
}
```

### AdminUpdateRoomRequest (바디)

```ts
interface AdminUpdateRoomRequest {
  startDate: string; // 'yyyy-MM-dd', 월요일만 허용 (프론트 검증 기준)
  endDate?: string | null; // 'yyyy-MM-dd' 또는 null, 일요일만 허용
  maxMembers: number; // 2 ~ 10
  minWeeklyWorkouts: number; // 1 ~ 7
  penaltyPerMiss: number; // 1,000 ~ 50,000
}
```

---

## 예시 응답

### GET /admin/members 예시

```json
{
  "data": {
    "content": [
      {
        "id": 1,
        "email": "admin@example.com",
        "nickname": "관리자",
        "profileUrl": "https://example.com/avatar.png",
        "totalWorkoutDays": 42,
        "totalPenalty": 0,
        "createdAt": "2024-01-01T12:34:56Z",
        "role": "ADMIN"
      }
    ],
    "last": true,
    "totalPages": 1,
    "number": 0,
    "size": 10
  },
  "message": "success",
  "success": true
}
```

### GET /admin/workout/rooms/{roomId} 예시 (요약)

```json
{
  "data": {
    "workoutRoomInfo": {
      "id": 10,
      "name": "1월 헬스 챌린지",
      "minWeeklyWorkouts": 3,
      "penaltyPerMiss": 5000,
      "startDate": "2024-01-01",
      "endDate": "2024-03-31",
      "maxMembers": 10,
      "currentMembers": 8,
      "ownerNickname": "방장",
      "isActive": true
    },
    "workoutRoomMembers": [],
    "currentMemberTodayWorkoutRecord": null
  },
  "message": "success",
  "success": true
}
```
