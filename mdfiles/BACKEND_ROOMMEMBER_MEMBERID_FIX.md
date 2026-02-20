# 백엔드 수정 요구사항: RoomMember 응답에 memberId 필드 추가

## 문제 상황

프론트엔드에서 `MyWorkoutRoom` 컴포넌트에서 멤버 프로필을 클릭할 때 타 유저의 운동 피드를 조회하는 API (`GET /api/members/{memberId}/workout-feed`)를 호출하고 있습니다.

현재 `RoomMember` 응답에는 `id` 필드만 있는데, 이것은 `workoutRoomMemberId` (운동방-멤버 관계 테이블의 ID)입니다. 하지만 운동 피드 조회 API는 실제 `Member` 테이블의 ID (`memberId`)를 요구하므로 타입 불일치로 인한 에러가 발생합니다.

## 수정 필요 사항

### 1. RoomMember 응답에 memberId 필드 추가

운동방 상세 정보를 반환하는 모든 API 응답에서 `RoomMember` 객체에 `memberId` 필드를 추가해야 합니다.

**수정 대상 API 엔드포인트:**
- `GET /api/workout/rooms/current` - 현재 운동방 조회
- `GET /api/workout/rooms/joined/{roomId}` - 가입한 운동방 상세 조회
- `GET /admin/workout/rooms/{roomId}` - 관리자용 운동방 상세 조회
- 기타 `WorkoutRoomDetail` 또는 `RoomMember[]` 배열을 반환하는 모든 엔드포인트

### 2. 응답 형식

**현재 응답 형식 (수정 전):**
```json
{
  "data": {
    "workoutRoomInfo": {
      "id": 1,
      "name": "운동방 1",
      ...
    },
    "workoutRoomMembers": [
      {
        "id": 100,  // workoutRoomMemberId (운동방-멤버 관계 ID)
        "nickname": "홍길동",
        "profileUrl": "https://example.com/profile.jpg",
        "totalWorkouts": 10,
        "weeklyWorkouts": 3,
        "totalPenalty": 0,
        "isOnBreak": false,
        "joinedAt": "2024-01-01T00:00:00",
        "workoutRecords": [...],
        "restInfoList": [...]
      }
    ],
    "currentMemberTodayWorkoutRecord": {...}
  }
}
```

**수정 후 응답 형식:**
```json
{
  "data": {
    "workoutRoomInfo": {
      "id": 1,
      "name": "운동방 1",
      ...
    },
    "workoutRoomMembers": [
      {
        "id": 100,  // workoutRoomMemberId (운동방-멤버 관계 ID) - 기존 필드 유지
        "memberId": 50,  // 실제 멤버 ID (Member 테이블의 ID) - 새로 추가 필요
        "nickname": "홍길동",
        "profileUrl": "https://example.com/profile.jpg",
        "totalWorkouts": 10,
        "weeklyWorkouts": 3,
        "totalPenalty": 0,
        "isOnBreak": false,
        "joinedAt": "2024-01-01T00:00:00",
        "workoutRecords": [...],
        "restInfoList": [...]
      }
    ],
    "currentMemberTodayWorkoutRecord": {...}
  }
}
```

### 3. DTO/Response 클래스 수정

백엔드에서 `RoomMember` DTO 또는 Response 클래스에 `memberId` 필드를 추가해야 합니다.

**예시 (Java/Spring Boot):**
```java
public class RoomMemberResponse {
    private Long id;  // workoutRoomMemberId
    private Long memberId;  // 실제 멤버 ID - 새로 추가
    private String nickname;
    private String profileUrl;
    private Integer totalWorkouts;
    private Integer weeklyWorkouts;
    private Integer totalPenalty;
    private Boolean isOnBreak;
    private LocalDateTime joinedAt;
    private List<WorkoutRecordResponse> workoutRecords;
    private List<RestInfoResponse> restInfoList;
    
    // getter, setter, constructor 등...
}
```

**매핑 예시:**
```java
// WorkoutRoomMember 엔티티에서 RoomMemberResponse로 매핑할 때
public RoomMemberResponse toResponse(WorkoutRoomMember workoutRoomMember) {
    return RoomMemberResponse.builder()
        .id(workoutRoomMember.getId())  // workoutRoomMemberId
        .memberId(workoutRoomMember.getMember().getId())  // 실제 Member ID
        .nickname(workoutRoomMember.getMember().getNickname())
        .profileUrl(workoutRoomMember.getMember().getProfileUrl())
        // ... 나머지 필드들
        .build();
}
```

### 4. 데이터베이스 관계

일반적으로 다음과 같은 관계 구조를 가정합니다:
- `WorkoutRoomMember` 테이블: 운동방-멤버 관계 테이블 (id = workoutRoomMemberId)
- `Member` 테이블: 실제 멤버 정보 테이블 (id = memberId)
- `WorkoutRoomMember.member_id` → `Member.id` (외래키 관계)

## 프론트엔드 변경 사항 (이미 완료됨)

프론트엔드에서는 이미 다음 수정이 완료되었습니다:

1. **타입 정의 수정** (`src/types/index.ts`):
   ```typescript
   export interface RoomMember {
     id: number; // workoutRoomMemberId
     memberId: number; // 실제 멤버 ID (Member 테이블의 ID)
     nickname: string;
     // ... 나머지 필드들
   }
   ```

2. **컴포넌트 수정** (`src/components/MyWorkoutRoom.tsx`):
   - `member.id` → `member.memberId`로 변경하여 실제 멤버 ID를 사용하도록 수정

## 우선순위

**높음 (즉시 수정 필요)**
- 운동방 상세 조회 API에서 `RoomMember` 응답에 `memberId` 필드 추가
- 프론트엔드가 이미 `memberId`를 사용하도록 수정되어 있어, 백엔드 수정이 없으면 런타임 에러 발생 가능

## 테스트 시나리오

1. 운동방 상세 조회 API 호출 시 `workoutRoomMembers` 배열의 각 항목에 `memberId` 필드가 포함되는지 확인
2. `memberId`가 실제 `Member` 테이블의 ID와 일치하는지 확인
3. `id` (workoutRoomMemberId)와 `memberId`가 서로 다른 값인지 확인 (일반적으로 다름)
4. 프론트엔드에서 멤버 프로필 클릭 시 운동 피드 조회가 정상적으로 동작하는지 확인

## 주의사항

- `id` 필드는 기존과 동일하게 `workoutRoomMemberId`를 유지해야 합니다 (하위 호환성)
- `memberId`는 필수 필드로 추가해야 합니다 (null이면 안 됨)
- 기존 API 응답 형식과의 호환성을 위해 `id` 필드는 제거하지 않습니다
