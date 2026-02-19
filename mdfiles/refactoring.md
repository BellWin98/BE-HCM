### 백엔드 수정 요구사항 정리 문서

---

## 1. 목표 요약

- **모든 유저**가:
    - 여러 개의 운동방 **생성 가능**
    - 여러 개의 운동방에 **동시 참여 가능**
- 방 입장 방식:
    - 기존: 방 목록에서 선택 + **비밀번호 입력**
    - 변경: **운동방 코드(입장 코드)를 직접 입력**해서 입장
- 방 코드 관련 권한:
    - **방장**: 코드 조회 + 복사 + **코드 변경(재발급)** 가능
    - **멤버**: 코드 조회 + 복사만 가능 (변경 불가)

---

## 2. API 변경 사항

### 2.1 방 생성

#### 현재(추정)

```http
POST /api/workout/rooms
Content-Type: application/json

{
  "name": "...",
  "minWeeklyWorkouts": 3,
  "penaltyPerMiss": 5000,
  "maxMembers": 10,
  "entryCode": "사용자 입력 비밀번호"
}
```

#### 변경 요구

- 클라이언트가 **랜덤 생성된 코드**를 `entryCode`로 전달하거나
- 또는 백엔드가 직접 생성해서 응답에 내려주는 방식 둘 중 하나를 선택

가급적 아래 방식으로 맞추면 좋음:

```http
POST /api/workout/rooms
Content-Type: application/json

{
  "name": "...",
  "minWeeklyWorkouts": 3,
  "penaltyPerMiss": 5000,
  "maxMembers": 10,
  "entryCode": "AB3K9P2L"   // 프론트에서 생성한 랜덤 코드
}
```

응답 예시:

```http
201 Created
Content-Type: application/json

{
  "id": 1,
  "name": "...",
  "minWeeklyWorkouts": 3,
  "penaltyPerMiss": 5000,
  "startDate": "...",
  "endDate": null,
  "maxMembers": 10,
  "currentMembers": 1,
  "ownerNickname": "방장닉",
  "isActive": true,
  "entryCode": "AB3K9P2L"
}
```

**요구사항**

- `entryCode`는 **방마다 유일(UNIQUE)** 해야 함.
- 코드 형식: 길이 6~10자, 영문/숫자 조합 (프론트는 대문자 + 숫자 위주 사용 예정)
- DB에 `workout_room.entry_code` 컬럼 추가 + UNIQUE 인덱스 필요

---

### 2.2 입장: 코드 기반 입장 API

#### 현재

```http
POST /api/workout/rooms/join/{roomId}?entryCode=xxx
```

#### 변경

```http
POST /api/workout/rooms/join
Content-Type: application/json

{
  "entryCode": "AB3K9P2L"
}
```

**동작**

1. `entryCode`로 방 조회
    - `SELECT * FROM workout_room WHERE entry_code = :entryCode AND is_active = true`
2. 방이 없거나 비활성 → 404 또는 400 에러
3. 해당 방의 멤버 수가 `maxMembers` 이상이면 → 400 에러 (정원 초과)
4. 이미 해당 방에 참여 중인 회원이면 → 409(이미 참여) 또는 200(멱등) 처리
5. 정상인 경우:
    - `workout_room_member` 레코드 생성
    - 필요 시 기존 단일 방 제한 로직(“이미 다른 방에 참여 중입니다”) 제거
6. 응답: 방 상세 또는 성공 메시지

---

### 2.3 운동방 코드 재발급 (방장 전용)

#### 신규 API

```http
POST /api/workout/rooms/{roomId}/regenerate-entry-code
Authorization: Bearer <token>
```

**동작**

1. 인증된 유저를 `currentMember`라고 할 때,
    - `room.owner_id` 또는 `room.ownerNickname` 기준으로 `currentMember`가 방장인지 검증
    - 방장이 아니면 → 403 Forbidden
2. 새로운 랜덤 `entryCode` 생성
    - 기존 코드와 **다른 값**
    - `entryCode` UNIQUE 제약 만족해야 함
3. DB 업데이트:
    - `UPDATE workout_room SET entry_code = :newCode WHERE id = :roomId`
4. 응답:

```json
{
  "id": 1,
  "name": "...",
  "entryCode": "NEWCODE12",
  ...
}
```

**주의**

- 코드가 변경되면 **기존 코드는 더 이상 유효하지 않아야 함**.

---

## 3. 응답 모델 변경

### 3.1 WorkoutRoom / WorkoutRoomDetail

#### 현재 타입(프론트 기준)

```ts
export interface WorkoutRoom {
  id: number;
  name: string;
  minWeeklyWorkouts: number;
  penaltyPerMiss: number;
  startDate: string;
  endDate: string | null;
  maxMembers: number;
  currentMembers: number;
  ownerNickname: string;
  isActive: boolean;
  // 여기에 entryCode 추가 필요
}
```

#### 변경

```ts
export interface WorkoutRoom {
  ...
  isActive: boolean;
  entryCode?: string; // 멤버에게만 노출
}

export interface WorkoutRoomDetail {
  workoutRoomInfo: WorkoutRoom | null;
  ... // 기존 필드 유지
}
```

**백엔드 요구사항**

- **방 멤버(참여자)** 에게 반환하는 응답에는 `entryCode` 포함
    - 예:
        - `GET /api/workout/rooms/current`
        - `GET /api/workout/rooms/joined/{roomId}`
        - `GET /api/workout/rooms/joined` (내가 참여한 방 목록)
- **비멤버**에게 노출하는 목록 (`GET /api/workout/rooms` 등) 에는 `entryCode` **포함하지 않기**
    - 공격자가 코드 브루트포스를 쉽게 하지 못하도록

---

## 4. 다중 방 참여 / 생성 규칙

### 4.1 다중 참여 허용

- 기존에 있었다면, 아래와 같은 제약 제거:
    - “한 사용자는 하나의 운동방에만 참여 가능”
- 이제:
    - 동일 유저가 여러 `workout_room_member` 레코드 가질 수 있어야 함
    - 단, **같은 방에 중복 참여는 금지** (UserId + RoomId UNIQUE)

### 4.2 다중 생성 허용

- 한 유저가 여러 개의 방을 생성할 수 있도록 제약 해제
    - 예: `owner_id` + `is_active` UNIQUE 같은 제약이 있었다면 제거
- `owner_id` 기준으로 여러 `workout_room` 레코드 허용

---

## 5. 기존 API 동작 및 호환성

### 5.1 `GET /api/workout/rooms/current`

- 현재 사용 용도:
    - “현재 내가 속해 있는 방” 하나를 가져오는 API
- 다중 방 구조에서:
    - **프론트는 이미 `/workout/rooms/joined` + `lastViewedWorkoutRoomId`를 사용**해서 특정 방을 선택/조회하도록 변경됨
    - `GET /current`는:
        - 유지하되, “가장 최근 참여한 방” 또는 “첫 번째 방”을 반환하는 정도로만 사용해도 무방
        - 장기적으로는 사용처를 줄이고 `/joined/{roomId}` 기반으로 조회하도록 유도

### 5.2 `/workout/rooms/joined` / `/workout/rooms/joined/{roomId}`

- **필수**:
    - `joined` 목록과 `joined/{roomId}` 상세 응답에 `entryCode` 포함 (멤버용)

---

## 6. 권한 및 보안 요약

- **코드 조회**
    - 방 멤버만 조회 가능
- **코드 재발급**
    - 방장만 가능 (`owner_id == currentUserId`)
- **코드로 입장**
    - 코드가 맞으면 해당 방에 멤버로 추가
    - 정원 초과, 비활성 방, 이미 참여 중 등은 적절한 에러 반환
- **코드 유일성**
    - DB 레벨 UNIQUE 인덱스 필수

---