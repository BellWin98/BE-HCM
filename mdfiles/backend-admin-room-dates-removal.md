# 백엔드 수정 요청: 관리자 운동방 수정 API에서 시작일/종료일 제거

## 배경

프론트엔드 관리자 페이지(`AdminRoomDetailPage`)에서 **시작일/종료일 편집 기능을 전부 제거**했습니다.  
관리자는 이제 **최대 참여 인원**, **주간 최소 운동 횟수**, **1회 누락당 벌금**만 수정할 수 있습니다.

현재 프론트는 호환을 위해 수정 요청 시 기존 `room.startDate`, `room.endDate`를 그대로 body에 넣어 보내고 있습니다.  
백엔드에서 시작일/종료일을 요청에서 제거하고, 수정 가능 필드를 정리해 달라는 요청입니다.

---

## 대상 API

- **엔드포인트**: `PUT /admin/workout/rooms/{roomId}` (관리자 운동방 규칙 수정)
- **역할**: 운동방의 규칙(정원, 주간 목표, 벌금 등) 수정

---

## 수정 요청 사항

### 1. 요청 body에서 시작일/종료일 제거

- **요청 DTO/스키마에서 제거할 필드**
  - `startDate`
  - `endDate`
- **수정 시 허용할 필드만 유지**
  - `maxMembers` (number, 필수)
  - `minWeeklyWorkouts` (number, 필수)
  - `penaltyPerMiss` (number, 필수)

즉, 관리자 수정 API는 **위 3개 필드만** 받고, `startDate`/`endDate`는 요청에서 받지 않습니다.  
DB의 기존 `start_date`/`end_date`는 **이 API로는 변경하지 않고 그대로 유지**합니다.

### 2. 응답 및 조회 API는 유지

- **응답(WorkoutRoom / 운동방 상세)**  
  - `startDate`, `endDate` 필드는 **그대로 포함**해도 됩니다.  
  - 다른 화면/기능에서 기간 표시 등으로 쓸 수 있으므로 제거하지 마세요.
- **GET 운동방 상세** (`GET /admin/workout/rooms/{roomId}` 등)  
  - 기존처럼 `startDate`, `endDate` 포함 유지.

### 3. 검증/비즈니스 로직

- 이 API의 핸들러/서비스에서 `startDate`/`endDate`에 대한 **검증(필수 여부, 형식, 월요일/일요일 등)은 제거**합니다.
- 수정 시 **변경하는 컬럼**: `max_members`, `min_weekly_workouts`, `penalty_per_miss` 만.
- `start_date`, `end_date`는 **UPDATE 문에 포함하지 않음** (기존 값 유지).

---

## 정리 (체크리스트)

- [ ] `PUT /admin/workout/rooms/{roomId}` 요청 DTO에서 `startDate`, `endDate` 필드 제거
- [ ] 해당 API의 업데이트 로직에서 `start_date`/`end_date` 갱신하지 않도록 수정 (또는 DTO에서 제거해 자동으로 반영되지 않게)
- [ ] 요청 검증에서 시작일/종료일 관련 검증 제거
- [ ] 응답 및 GET 운동방 상세 API의 `startDate`/`endDate`는 유지 (제거하지 않음)

---

## 참고: 프론트엔드 변경 요약

- **수정된 파일**: `src/pages/admin/AdminRoomDetailPage.tsx`
- **제거된 UI**: 시작일 선택, 종료일 사용 여부 체크, 종료일 선택
- **수정 필드**: 최대 참여 인원, 주간 최소 운동 횟수, 1회 누락당 벌금만 폼에 존재
- **다음 단계**: 백엔드가 위와 같이 수정되면, 프론트엔드에서도 `updateAdminWorkoutRoom` 호출 시 `startDate`/`endDate`를 body에서 제거하고, 타입 `AdminUpdateRoomRequest`에서 해당 필드를 제거할 예정입니다.
