## 벌금 내역 기간 필터 백엔드 작업 정리

### 1. 개요

- 운동방 **벌금 현황**에서 프론트가 **기간(연도별/월간/주간/기간 지정)** 을 선택해 조회할 수 있도록, 백엔드에서 **쿼리 파라미터 기반 기간 필터링**을 지원해야 합니다.
- 프론트는 `GET /penalty/rooms/:roomId/records` 호출 시 **`startDate`, `endDate`** 쿼리 파라미터를 선택적으로 보내며, 두 값이 모두 있을 때만 서버에서 기간 필터를 적용합니다. 하나라도 없으면 **전체 레코드**를 반환합니다(기존 동작 유지).

---

### 2. 프론트엔드에서 사용하는 API 스펙

- **요청 메서드**: `GET`
- **엔드포인트**: `/penalty/rooms/:roomId/records` (기존과 동일)
- **경로 파라미터**
  - `roomId`: `number` — 운동방 ID
- **쿼리 파라미터** (선택)
  - `startDate`: `string` (yyyy-MM-dd) — 조회 기간 시작일
  - `endDate`: `string` (yyyy-MM-dd) — 조회 기간 종료일

#### 동작

- **`startDate`, `endDate`가 둘 다 있는 경우**: 주 단위 벌금 레코드 중, **레코드의 주차 기간(`weekStartDate` ~ `weekEndDate`)이 [startDate, endDate]와 겹치는 것만** 반환합니다.
- **하나라도 없거나 미전달인 경우**: 기존처럼 **해당 방의 벌금 레코드 전체**를 반환합니다(하위 호환).

---

### 3. 프론트 호출 형태

- **옵션용 전체 조회** (연/월/주 드롭다운 옵션 채우기):
  - `GET /penalty/rooms/{roomId}/records`
  - 쿼리 파라미터 없음 → 전체 레코드 반환 기대

- **기간별 목록 조회** (선택한 기간에 해당하는 레코드만):
  - 연도별: `GET /penalty/rooms/{roomId}/records?startDate=2024-01-01&endDate=2024-12-31`
  - 월간: `GET /penalty/rooms/{roomId}/records?startDate=2024-03-01&endDate=2024-03-31`
  - 주간: `GET /penalty/rooms/{roomId}/records?startDate=2024-03-04&endDate=2024-03-10` (해당 주 월~일)
  - 기간 지정: `GET /penalty/rooms/{roomId}/records?startDate=2024-02-01&endDate=2024-02-29` (사용자 선택값 그대로)

---

### 4. 필터 조건 (백엔드 구현 시)

- 각 벌금 레코드는 **주 단위**로 `weekStartDate`, `weekEndDate`(ISO 문자열 또는 일자)를 가집니다.
- **포함 조건**: 조회 구간 [startDate, endDate]와 **주차 구간이 하루라도 겹치는** 레코드만 반환합니다.
  - 겹침 조건:  
    `(레코드 weekStartDate의 일자 부분) <= endDate` **그리고**  
    `(레코드 weekEndDate의 일자 부분) >= startDate`
- 일자 비교 시 **날짜 문자열(yyyy-MM-dd) 비교** 또는 서버에서 사용하는 일자 타입으로 통일해 비교하면 됩니다.

---

### 5. 응답 스펙 (변경 없음)

- 응답 타입: **벌금 레코드 배열** (기존과 동일).
- 프론트에서 기대하는 항목: `id`, `workoutRoomMemberId`, `weekStartDate`, `weekEndDate`, `requiredWorkouts`, `actualWorkouts`, `penaltyAmount`, `isPaid`, `paidAt`(선택) 등 기존 `PenaltyRecord` 구조 유지.

```json
[
  {
    "id": 1,
    "workoutRoomMemberId": "10",
    "weekStartDate": "2024-03-04T00:00:00",
    "weekEndDate": "2024-03-10T23:59:59",
    "requiredWorkouts": 3,
    "actualWorkouts": 2,
    "penaltyAmount": 5000,
    "isPaid": false,
    "paidAt": null
  }
]
```

- 페이징은 적용하지 않으며, **해당 조건을 만족하는 레코드 전체**를 배열로 반환하면 됩니다.

---

### 6. 백엔드 구현 체크리스트

- [ ] `GET /penalty/rooms/:roomId/records` 핸들러에서 쿼리 파라미터 `startDate`, `endDate` 수신
- [ ] `startDate`, `endDate`가 **둘 다 존재할 때만** 기간 필터 적용
- [ ] 필터 조건: `weekStartDate`(일자) ≤ `endDate` 이고 `weekEndDate`(일자) ≥ `startDate` 인 레코드만 조회
- [ ] 하나라도 없으면 기존처럼 해당 방의 벌금 레코드 전체 반환
- [ ] 응답 DTO/구조는 기존과 동일하게 유지

---

### 7. 참고 (프론트 로직)

- 기간 타입별로 프론트가 계산해 보내는 값:
  - **연도별**: `startDate = YYYY-01-01`, `endDate = YYYY-12-31`
  - **월간**: 해당 월 1일 ~ 해당 월 마지막 날
  - **주간**: 선택한 주의 `yyyy-MM-dd~yyyy-MM-dd` 문자열을 split한 start/end
  - **기간 지정**: 사용자가 선택한 시작일/종료일 그대로

백엔드는 **항상 `startDate`와 `endDate` 두 개만** 받아서 위 겹침 조건으로 필터링하면 됩니다.
