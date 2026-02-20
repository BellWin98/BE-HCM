# 백엔드 API 구현 요구사항

## 개요
프론트엔드에서 타 유저 운동 기록에 좋아요/댓글 기능을 구현하기 위해 필요한 백엔드 API 스펙입니다.

---

## 1. 타 유저 운동 피드 조회 API

### 엔드포인트
```
GET /api/members/{memberId}/workout-feed
```

### 요청 파라미터
- **Path Parameter**
    - `memberId` (number, required): 조회할 멤버의 ID

- **Query Parameter**
    - `page` (number, optional, default: 0): 페이지 번호 (0부터 시작)
    - `size` (number, optional, default: 20): 페이지 크기

### 요청 예시
```
GET /api/members/123/workout-feed?page=0&size=20
Authorization: Bearer {accessToken}
```

### 응답 형식

**성공 응답 (200 OK)**
```json
{
  "data": {
    "content": [
      {
        "id": 1,
        "workoutDate": "2024-01-15",
        "workoutTypes": ["헬스(가슴)", "헬스(등)"],
        "duration": 60,
        "imageUrls": ["https://example.com/image1.jpg", "https://example.com/image2.jpg"],
        "description": "오늘 운동 완료!",
        "likes": 5,
        "comments": 2,
        "isLiked": false,
        "createdAt": "2024-01-15T10:30:00",
        "roomName": "운동방 1"
      }
    ],
    "last": false,
    "totalPages": 5,
    "number": 0,
    "size": 20
  }
}
```

또는 배열 형식 (페이징 없이):
```json
{
  "data": [
    {
      "id": 1,
      "workoutDate": "2024-01-15",
      "workoutTypes": ["헬스(가슴)"],
      "duration": 60,
      "imageUrls": ["https://example.com/image1.jpg"],
      "likes": 5,
      "comments": 2,
      "isLiked": false,
      "createdAt": "2024-01-15T10:30:00"
    }
  ]
}
```

### 권한 처리
- **인증 필요**: Bearer 토큰 필수
- **권한 검증**:
    - 요청한 사용자와 조회 대상 멤버가 **같은 운동방에 속해 있는지** 확인
    - 같은 방에 속하지 않으면 `403 Forbidden` 또는 `404 Not Found` 반환
- **본인 피드 조회**: 본인 피드 조회는 기존 `/api/members/workout-feed` 엔드포인트 사용 (변경 없음)

### 에러 응답
- `401 Unauthorized`: 인증 토큰 없음 또는 만료
- `403 Forbidden`: 같은 방 멤버가 아님
- `404 Not Found`: 해당 멤버를 찾을 수 없음

---

## 2. 댓글 조회 API

### 엔드포인트
```
GET /api/workouts/{workoutId}/comments
```

### 요청 파라미터
- **Path Parameter**
    - `workoutId` (number, required): 운동 기록 ID

### 요청 예시
```
GET /api/workouts/456/comments
Authorization: Bearer {accessToken}
```

### 응답 형식

**성공 응답 (200 OK)**
```json
{
  "data": [
    {
      "id": 1,
      "workoutId": 456,
      "memberId": 789,
      "nickname": "홍길동",
      "profileUrl": "https://example.com/profile.jpg",
      "content": "멋진 운동이네요!",
      "createdAt": "2024-01-15T11:00:00"
    },
    {
      "id": 2,
      "workoutId": 456,
      "memberId": 101,
      "nickname": "김철수",
      "profileUrl": null,
      "content": "화이팅!",
      "createdAt": "2024-01-15T11:30:00"
    }
  ]
}
```

### 권한 처리
- **인증 필요**: Bearer 토큰 필수
- **권한 검증**:
    - 해당 운동 기록을 볼 수 있는 권한이 있는지 확인 (같은 방 멤버인지)
    - 권한이 없으면 `403 Forbidden` 반환

### 에러 응답
- `401 Unauthorized`: 인증 토큰 없음 또는 만료
- `403 Forbidden`: 해당 운동 기록에 접근 권한 없음
- `404 Not Found`: 해당 운동 기록을 찾을 수 없음

---

## 3. 댓글 작성 API

### 엔드포인트
```
POST /api/workouts/{workoutId}/comments
```

### 요청 파라미터
- **Path Parameter**
    - `workoutId` (number, required): 운동 기록 ID

- **Request Body**
```json
{
  "content": "댓글 내용"
}
```

### 요청 예시
```
POST /api/workouts/456/comments
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "content": "멋진 운동이네요!"
}
```

### 응답 형식

**성공 응답 (201 Created 또는 200 OK)**
```json
{
  "data": {
    "id": 3,
    "workoutId": 456,
    "memberId": 123,
    "nickname": "작성자닉네임",
    "profileUrl": "https://example.com/profile.jpg",
    "content": "멋진 운동이네요!",
    "createdAt": "2024-01-15T12:00:00"
  }
}
```

또는 void 응답:
```json
{
  "data": null,
  "message": "댓글이 작성되었습니다."
}
```

### 권한 처리
- **인증 필요**: Bearer 토큰 필수
- **권한 검증**:
    - 해당 운동 기록을 볼 수 있는 권한이 있는지 확인 (같은 방 멤버인지)
    - 권한이 없으면 `403 Forbidden` 반환
- **본인 게시물 댓글**: 본인 게시물에도 댓글 작성 가능 (제한 없음)

### 유효성 검증
- `content` 필수, 최소 1자 이상, 최대 길이 제한 (예: 500자)
- 빈 문자열 또는 공백만 있으면 `400 Bad Request` 반환

### 에러 응답
- `400 Bad Request`: 댓글 내용이 없거나 유효하지 않음
- `401 Unauthorized`: 인증 토큰 없음 또는 만료
- `403 Forbidden`: 해당 운동 기록에 접근 권한 없음
- `404 Not Found`: 해당 운동 기록을 찾을 수 없음

---

## 4. 댓글 삭제 API

### 엔드포인트
```
DELETE /api/workouts/comments/{commentId}
```

### 요청 파라미터
- **Path Parameter**
    - `commentId` (number, required): 댓글 ID

### 요청 예시
```
DELETE /api/workouts/comments/3
Authorization: Bearer {accessToken}
```

### 응답 형식

**성공 응답 (200 OK 또는 204 No Content)**
```json
{
  "data": null,
  "message": "댓글이 삭제되었습니다."
}
```

### 권한 처리
- **인증 필요**: Bearer 토큰 필수
- **권한 검증**:
    - 댓글 작성자 본인만 삭제 가능
    - 본인 댓글이 아니면 `403 Forbidden` 반환

### 에러 응답
- `401 Unauthorized`: 인증 토큰 없음 또는 만료
- `403 Forbidden`: 본인 댓글이 아님
- `404 Not Found`: 해당 댓글을 찾을 수 없음

---

## 5. 좋아요 API (기존 API 확인)

### 좋아요 추가
```
POST /api/workouts/{workoutId}/like
Authorization: Bearer {accessToken}
```

### 좋아요 취소
```
DELETE /api/workouts/{workoutId}/like
Authorization: Bearer {accessToken}
```

**참고**: 이미 구현되어 있다고 가정하지만, 다음 사항 확인 필요:
- 같은 방 멤버의 운동 기록에만 좋아요 가능한지 권한 검증
- 중복 좋아요 방지 (이미 좋아요한 경우 재요청 시 에러 또는 무시)

---

## 6. 데이터베이스 스키마 제안

### 댓글 테이블 (예시)
```sql
CREATE TABLE workout_comments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workout_id BIGINT NOT NULL,
  member_id BIGINT NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  
  FOREIGN KEY (workout_id) REFERENCES workouts(id) ON DELETE CASCADE,
  FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE,
  
  INDEX idx_workout_id (workout_id),
  INDEX idx_member_id (member_id),
  INDEX idx_created_at (created_at)
);
```

### 좋아요 테이블 (예시)
```sql
CREATE TABLE workout_likes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workout_id BIGINT NOT NULL,
  member_id BIGINT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  
  FOREIGN KEY (workout_id) REFERENCES workouts(id) ON DELETE CASCADE,
  FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE,
  
  UNIQUE KEY uk_workout_member (workout_id, member_id),
  INDEX idx_workout_id (workout_id),
  INDEX idx_member_id (member_id)
);
```

---

## 7. 구현 우선순위

1. **높음 (필수)**
    - 타 유저 운동 피드 조회 API (`GET /api/members/{memberId}/workout-feed`)
    - 댓글 조회 API (`GET /api/workouts/{workoutId}/comments`)
    - 댓글 작성 API (`POST /api/workouts/{workoutId}/comments`)

2. **중간 (권장)**
    - 댓글 삭제 API (`DELETE /api/workouts/comments/{commentId}`)
    - 좋아요 API 권한 검증 강화

3. **낮음 (선택)**
    - 댓글 수정 기능
    - 댓글 신고 기능

---

## 8. 추가 고려사항

### 성능 최적화
- 댓글 목록 조회 시 페이징 지원 고려 (현재는 전체 조회)
- 운동 피드 조회 시 `likes`, `comments`, `isLiked` 필드가 정확히 계산되어 반환되는지 확인
- 댓글 작성/삭제 시 해당 운동 기록의 `comments` 수 자동 업데이트

### 실시간 업데이트 (선택)
- 댓글 작성 시 WebSocket 또는 Server-Sent Events로 실시간 알림
- 좋아요 수 실시간 업데이트

### 보안
- XSS 방지를 위한 댓글 내용 sanitization
- SQL Injection 방지
- Rate Limiting (댓글 작성 빈도 제한)

---

## 9. 테스트 시나리오

### 타 유저 피드 조회
1. 같은 방 멤버의 피드 조회 → 성공
2. 다른 방 멤버의 피드 조회 → 403 Forbidden
3. 존재하지 않는 멤버 ID → 404 Not Found
4. 인증 토큰 없음 → 401 Unauthorized

### 댓글 작성
1. 같은 방 멤버의 운동 기록에 댓글 작성 → 성공
2. 다른 방 멤버의 운동 기록에 댓글 작성 → 403 Forbidden
3. 빈 댓글 작성 → 400 Bad Request
4. 본인 게시물에 댓글 작성 → 성공 (가능해야 함)

### 댓글 삭제
1. 본인 댓글 삭제 → 성공
2. 다른 사람 댓글 삭제 → 403 Forbidden
3. 존재하지 않는 댓글 ID → 404 Not Found

---

## 10. 프론트엔드 연동 정보

프론트엔드에서 사용하는 API 클라이언트:
- 파일: `src/lib/api.ts`
- 메서드명:
    - `getMemberWorkoutFeed(memberId, page, size)`
    - `getWorkoutComments(workoutId)`
    - `createWorkoutComment(workoutId, content)`
    - `deleteWorkoutComment(commentId)`

응답 형식은 위에 명시된 형식을 따르면 됩니다. 프론트엔드는 `response.data` 또는 `response.data.data`에서 실제 데이터를 추출합니다.
