# 백엔드 구현 요구사항: 관리자 삭제 기능

## 개요
프론트엔드에서 관리자 페이지의 회원 삭제 및 운동방 삭제 기능이 구현되었습니다. 이에 대응하는 백엔드 API 엔드포인트를 구현해야 합니다.

## 구현 필요 API 엔드포인트

### 1. 회원 삭제 API

**엔드포인트**: `DELETE /api/admin/members/{memberId}`

**요청:**
- Method: `DELETE`
- Path Parameter:
    - `memberId` (number): 삭제할 회원 ID
- Headers:
    - `Authorization: Bearer {accessToken}` (필수)
    - `Content-Type: application/json`

**응답:**
- 성공 시 (200 OK):
  ```json
  {
    "data": null,
    "message": "회원이 삭제되었습니다."
  }
  ```

- 실패 시:
    - 401 Unauthorized: 인증되지 않은 사용자
    - 403 Forbidden: ADMIN 권한이 없는 사용자
    - 404 Not Found: 존재하지 않는 회원 ID
    - 400 Bad Request: 삭제할 수 없는 상태 (예: 본인 계정, 마지막 ADMIN 등)

**비즈니스 로직:**
1. 요청한 사용자가 ADMIN 권한을 가지고 있는지 확인
2. 삭제 대상 회원이 존재하는지 확인
3. 삭제 가능 여부 검증:
    - 본인 계정 삭제는 허용할지 결정 (현재 프론트엔드는 경고만 표시)
    - 마지막 ADMIN 계정 삭제 방지 (선택사항)
4. 회원 삭제 처리:
    - 관련 데이터 정리 (운동방 참여, 운동 기록, 벌금 기록 등)
    - 하드 삭제(hard delete)
5. 성공 응답 반환

**주의사항:**
- 회원 삭제 시 관련된 모든 데이터(운동방 참여, 운동 기록, 벌금 등)의 처리 방식을 결정해야 합니다.
- CASCADE 삭제 또는 NULL 처리 등 데이터베이스 제약 조건을 확인해야 합니다.

---

### 2. 운동방 삭제 API

**엔드포인트**: `DELETE /api/admin/workout/rooms/{roomId}`

**요청:**
- Method: `DELETE`
- Path Parameter:
    - `roomId` (number): 삭제할 운동방 ID
- Headers:
    - `Authorization: Bearer {accessToken}` (필수)
    - `Content-Type: application/json`

**응답:**
- 성공 시 (200 OK):
  ```json
  {
    "data": null,
    "message": "운동방이 삭제되었습니다."
  }
  ```

- 실패 시:
    - 401 Unauthorized: 인증되지 않은 사용자
    - 403 Forbidden: ADMIN 권한이 없는 사용자
    - 404 Not Found: 존재하지 않는 운동방 ID
    - 400 Bad Request: 삭제할 수 없는 상태 (예: 활성 상태의 운동방, 참여 중인 회원이 있는 경우 등)

**비즈니스 로직:**
1. 요청한 사용자가 ADMIN 권한을 가지고 있는지 확인
2. 삭제 대상 운동방이 존재하는지 확인
3. 삭제 가능 여부 검증:
    - 현재 참여 중인 회원 수 확인 (`currentMembers > 0`인 경우 경고 또는 차단)
    - 활성 상태(`isActive = true`)인 운동방 삭제 허용 여부 결정
4. 운동방 삭제 처리:
    - 관련 데이터 정리 (참여 회원, 운동 기록, 채팅, 벌금 기록 등)
    - 하드 삭제(hard delete)
5. 성공 응답 반환

**주의사항:**
- 운동방 삭제 시 참여 중인 회원들의 처리 방식을 결정해야 합니다.
- 운동방의 운동 기록, 채팅, 벌금 기록 등 관련 데이터의 처리 방식을 결정해야 합니다.
- CASCADE 삭제 또는 NULL 처리 등 데이터베이스 제약 조건을 확인해야 합니다.

---

## 공통 요구사항

### 인증 및 권한
- 두 API 모두 ADMIN 권한이 필요합니다.
- JWT 토큰을 통한 인증이 필요합니다.
- 권한 검증은 기존 Admin API들과 동일한 방식으로 구현하면 됩니다.

### 에러 처리
- 일관된 에러 응답 형식 유지
- 적절한 HTTP 상태 코드 사용
- 명확한 에러 메시지 제공

---

## 프론트엔드 연동 정보

### API 클라이언트 구현 위치
- 파일: `src/lib/api.ts`
- 메서드:
    - `deleteAdminMember(memberId: number): Promise<void>`
    - `deleteAdminWorkoutRoom(roomId: number): Promise<void>`

### 사용 위치
- 회원 삭제: `src/pages/admin/AdminMembersPage.tsx`
- 운동방 삭제: `src/pages/admin/AdminRoomsPage.tsx`

### 프론트엔드 동작
- 삭제 전 확인 다이얼로그 표시
- 삭제 성공 시 토스트 메시지 표시 및 목록 자동 새로고침
- 삭제 실패 시 에러 메시지 표시

---

## 구현 우선순위

1. **회원 삭제 API** (`DELETE /api/admin/members/{memberId}`)
    - 회원 삭제 시 관련 데이터 처리 방안 결정 필요
    - 본인 계정 삭제 허용 여부 결정 필요

2. **운동방 삭제 API** (`DELETE /api/admin/workout/rooms/{roomId}`)
    - 운동방 삭제 시 참여 회원 처리 방안 결정 필요
    - 활성 운동방 삭제 허용 여부 결정 필요

---

## 확인 필요 사항

1. **데이터 삭제 정책**
    - 하드 삭제
    - 관련 데이터(운동 기록, 벌금 등) 처리 방식

2. **비즈니스 규칙**
    - 본인 계정 삭제 허용 여부
    - 마지막 ADMIN 계정 삭제 방지 여부
    - 참여 중인 회원이 있는 운동방 삭제 허용 여부
    - 활성 상태 운동방 삭제 허용 여부

3. **데이터베이스 제약 조건**
    - 외래 키 제약 조건 확인
    - CASCADE 삭제 설정 여부

---

## 테스트 케이스 제안

### 회원 삭제 API
- [ ] ADMIN 권한이 있는 사용자가 회원 삭제 성공
- [ ] 일반 사용자가 회원 삭제 시도 시 403 에러
- [ ] 존재하지 않는 회원 ID로 삭제 시도 시 404 에러
- [ ] 본인 계정 삭제 시도 (허용/차단 정책에 따라)
- [ ] 마지막 ADMIN 계정 삭제 시도 (차단 여부 확인)

### 운동방 삭제 API
- [ ] ADMIN 권한이 있는 사용자가 운동방 삭제 성공
- [ ] 일반 사용자가 운동방 삭제 시도 시 403 에러
- [ ] 존재하지 않는 운동방 ID로 삭제 시도 시 404 에러
- [ ] 참여 중인 회원이 있는 운동방 삭제 시도 (허용/차단 정책에 따라)
- [ ] 활성 상태 운동방 삭제 시도 (허용/차단 정책에 따라)