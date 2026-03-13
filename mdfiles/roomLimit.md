## 운동방 동시 참여 개수 제한 (백엔드 스펙)

### 비즈니스 요구사항 정리

- **ADMIN 이 아닌 일반 회원**은 **동시에 최대 3개의 운동방에만 참여 가능**하다.
- 여기서 “참여”는 다음을 모두 포함한다.
  - 자신이 **방장(Owner)** 인 운동방
  - 초대 코드/입장 코드 등으로 **멤버로 참여** 중인 운동방
- 일반 회원이 이미 3개의 운동방에 참여 중인 상태라면:
  - **새 운동방을 생성할 수 없다.**
  - **다른 운동방에 새로 입장(참여)할 수 없다.**
- ADMIN 계정은 위 제한에서 **예외**이며, 참여 중인 방 개수와 관계없이 생성/입장이 가능하다.

프론트엔드는 현재 위 조건을 기준으로 **버튼 비활성화 및 토스트 메시지** 정도의 UX 가드를 하고 있고, **실제 제약 보장은 백엔드가 책임져야 한다.**

---

### 관련 도메인 모델 (가정)

> 실제 엔티티/테이블 이름은 백엔드 구현에 맞게 매핑 필요. 아래는 개념적인 예시이다.

- `Member` 또는 `User`
  - 필드 예시: `id`, `role` (`ADMIN` / `USER` 등)
- `WorkoutRoom`
  - 필드 예시: `id`, `name`, `ownerId` 등
- `WorkoutRoomMember` (회원-운동방 매핑 테이블)
  - 필드 예시: `id`, `workoutRoomId`, `memberId`, `roleInRoom` 등

**“참여 중인 방 수” 계산 시 포함 범위:**

- 조건 1: `WorkoutRoom.ownerId = member.id` 인 방
- 조건 2: `WorkoutRoomMember.memberId = member.id` 인 방
- 필요 시 중복 제거: 어떤 방에 대해 owner + member 관계가 동시에 잡혀도 **해당 방은 1개로만 카운트**해야 한다.

---

### 백엔드에서 제약을 적용해야 하는 시점

프론트 코드 기준으로 최소 다음 API들에서 **서버 단 검증이 필요**하다.

#### 1. 운동방 생성 API

- **엔드포인트 예시**
  - `POST /api/workout-rooms`
- **현재 프론트 사용 위치**
  - `CreateRoomPage` 에서 방 생성 폼 제출 시 호출.
- **필수 검증 로직**
  1. 인증된 사용자 조회 (`currentUser` / `member` 등).
  2. `member.role !== 'ADMIN'` 인 경우만 제한 적용.
  3. 해당 멤버가 현재 참여 중인 운동방 개수를 조회:
     - `ownedCount = count(WorkoutRoom where ownerId = member.id)`
     - `memberCount = count(WorkoutRoomMember where memberId = member.id)`
     - `joinedRoomCount = count(distinct roomId from (owned ∪ member))`
  4. `joinedRoomCount >= 3` 이면, **방 생성 거부**.
     - HTTP Status: `400 Bad Request` 또는 비즈니스 규칙에 맞는 코드
     - 에러 코드 예시:
       - `ROOM_LIMIT_EXCEEDED`
     - 메시지 예시(백엔드 기본): `"일반 회원은 최대 3개의 운동방에만 참여할 수 있습니다."`
  5. 통과 시 기존 로직대로 방 생성 처리.

#### 2. 운동방 입장(코드로 입장) API

- **엔드포인트 예시**
  - `POST /api/workout-rooms/join` 또는 `POST /api/workout-rooms/join-by-code`
  - 요청 바디 예시: `{ "entryCode": "ABCDEFGH" }`
- **현재 프론트 사용 위치**
  - `DashboardPage`에서:
    - `RoomCodeDialog` 제출 시 `roomJoin.handleCodeSubmit`에서 호출.
    - `AvailableRoomsDialog`의 "코드로 입장" 버튼 → 코드 입력 다이얼로그 → 위 API 호출.
    - `AvailableWorkoutRooms`의 "코드로 입장" 버튼 → 동일 플로우.
- **필수 검증 로직**
  1. 인증된 사용자 조회.
  2. `member.role !== 'ADMIN'` 인 경우만 제한 적용.
  3. 위와 동일하게 현재 참여 중인 운동방 개수(`joinedRoomCount`) 조회.
  4. `joinedRoomCount >= 3` 이면, **해당 방에 새로 참여하는 것을 거부**.
     - HTTP Status: `400 Bad Request`
     - 에러 코드 예시:
       - `ROOM_JOIN_LIMIT_EXCEEDED`
     - 에러 메시지 예시:
       - `"일반 회원은 최대 3개의 운동방에만 참여할 수 있습니다."`
  5. 통과 시:
     - 코드로 대상 `WorkoutRoom` 조회.
     - 이미 참여 중인 방인지(`WorkoutRoomMember`에 레코드 존재 여부 등) 검증.
     - 참여 중이 아니라면 `WorkoutRoomMember` 생성 후 성공 응답.

> 참고: 이미 3개 참여 중인 사용자가 프론트 가드를 우회해 직접 API를 호출하더라도, 위 검증으로 인해 서버에서 확실하게 차단해야 한다.

---

### 에러 응답 포맷 제안

프론트에서 “참여 제한 초과”를 **특정 케이스로 구분 처리**할 수 있도록, 에러 응답에 **일관된 코드 필드**를 포함시키는 것이 좋다.

- 예시 JSON 응답:

```json
{
  "code": "ROOM_JOIN_LIMIT_EXCEEDED",
  "message": "일반 회원은 최대 3개의 운동방에만 참여할 수 있습니다."
}
```

프론트에서는 위 `code` 값을 체크해서, 이미 구현된 토스트 문구와 맞춰 사용하거나, 필요시 더 친절한 안내를 띄울 수 있다.

---

### ADMIN 예외 처리

- `member.role === 'ADMIN'` 인 경우:
  - 방 생성 API, 방 입장 API 모두 **참여 개수와 관계없이 허용**한다.
  - 따라서 위 제약 로직은 반드시 `if (role !== ADMIN)` 블록 안에서만 실행해야 한다.

---

### 추가 고려 사항

1. **트랜잭션 / 동시성**
   - 두 개 이상의 방에 거의 동시에 입장 요청을 보낼 수 있는 상황(모바일, 멀티탭 등)을 고려하면,
   - `joinedRoomCount` 조회와 새로운 `WorkoutRoomMember` 생성 사이에는 **트랜잭션** 또는 **고유 제약조건 + 재검증** 전략이 필요하다.
2. **비활성/종료된 방 포함 여부**
   - 현 프론트 구현은 “현재 참여 중인 방”을 어떻게 정의하는지 명확치 않으나,
   - 백엔드는 정책을 명확히 정해야 한다:
     - 예시 1: 이미 종료된 방(`isActive = false` 또는 종료일 지난 방)은 참여 개수에서 제외한다.
     - 예시 2: 단순히 `WorkoutRoomMember` 에 남아 있으면 모두 참여로 간주한다.
   - 프론트 기준으로는 “실제로 더 이상 활동하지 않는 방”이 있다면 **제한에서 제외하는 쪽**이 UX 상 자연스러울 수 있다.
3. **탈퇴(방 나가기) 기능과 연동**
   - 사용자가 방에서 나가면, 해당 방은 참여 개수에서 빠져야 한다.
   - 방 나가기 API가 있다면, 그 이후에는 새로운 방 생성/입장이 가능해야 한다.

---

### 프론트 구현과의 정합성 메모

- 프론트는 `DashboardPage`에서:
  - `joinedRooms.length >= 3 && !isAdmin` 일 때:
    - "방 만들기" 버튼 비활성 + 클릭 시 토스트.
    - "코드로 입장" 버튼 클릭 시 다이얼로그를 열지 않고 토스트만 표시.
    - "모든 운동방" 다이얼로그에서, 아직 참여하지 않은 방 카드에도 “최대 3개의 운동방에만 참여할 수 있습니다” 버튼/문구 노출.
- 하지만 이 모든 것은 **클라이언트 가드**에 불과하므로,
  - 백엔드는 위에서 정리한 생성/입장 API 레벨 제약을 꼭 적용해야 **정책이 실제로 보장**된다.
