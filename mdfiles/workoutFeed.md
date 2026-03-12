## 마이페이지 운동 인증 피드 기간 필터 백엔드 작업 정리

### 1. 개요

- 마이페이지 `운동 인증 피드`에서 프론트가 **기간 필터(전체 / 이번 주 / 이번 달)** 를 선택해 조회할 수 있도록 백엔드에서 기간 필터링을 지원해야 합니다.
- 프론트는 `/api/members/workout-feed` 엔드포인트에 **`periodType` 쿼리 파라미터**를 추가로 보내고, 응답은 기존과 동일한 **페이지 응답(`PageResponse<WorkoutFeedItem>`)** 형태를 유지합니다.

---

### 2. 프론트엔드에서 사용하는 API 스펙

- 요청 메서드: `GET`
- 엔드포인트: `/api/members/workout-feed`
- 쿼리 파라미터:
  - **필수**
    - `page`: `number` (0-base 페이지 번호)
    - `size`: `number` (페이지 크기)
  - **선택**
    - `periodType`: `"ALL" | "WEEK" | "MONTH"`
      - 미전달 또는 `"ALL"`인 경우: **전체 기간** 대상으로 페이징
      - `"WEEK"`인 경우: **이번 주(현재 날짜 기준)** 운동 인증만 대상으로 페이징
      - `"MONTH"`인 경우: **이번 달(현재 날짜 기준)** 운동 인증만 대상으로 페이징

#### 프론트 코드 기준 호출 형태

- 초기 로딩 (전체 보기):
  - `GET /api/members/workout-feed?page=0&size=20&periodType=ALL`
- 기간 변경 시:
  - 전체: `GET /api/members/workout-feed?page=0&size=20&periodType=ALL`
  - 이번 주: `GET /api/members/workout-feed?page=0&size=20&periodType=WEEK`
  - 이번 달: `GET /api/members/workout-feed?page=0&size=20&periodType=MONTH`
- 더보기(무한 스크롤):
  - 동일한 `periodType`으로 `page`만 증가시키면서 호출  
    예) `page=1`, `page=2` …

---

### 3. 응답 스펙 (변경 없음)

- 응답 타입: `PageResponse<WorkoutFeedItem>`
- JSON 예시:

```json
{
  "content": [
    {
      "id": 1,
      "workoutDate": "2026-03-10",
      "workoutTypes": ["헬스(가슴)", "러닝"],
      "duration": 60,
      "imageUrls": ["https://.../image1.jpg"],
      "description": "가슴 + 러닝",
      "likes": 10,
      "comments": 2,
      "isLiked": false,
      "createdAt": "2026-03-10T10:00:00",
      "roomName": "평일 헬스방"
    }
  ],
  "last": false,
  "totalPages": 5,
  "number": 0,
  "size": 20
}
```

- **주의점**
  - 프론트는 `content` 배열이 비어 있으면 더 이상 데이터가 없다고 판단하고, `last` 플래그가 `true`인 경우에도 추가 로딩을 중단합니다.
  - 타입 구조는 기존 구현과 동일하게 유지해야 합니다.

---

### 4. 기간 필터링 로직 정의

#### 4.1. 기준 날짜

- 기준: **현재 서버 시각(LocalDate.now 또는 ZonedDateTime.now 기준)**
  - 회원별 타임존 처리가 필요하면, 회원/요청 기준 타임존으로 변환 후 계산.

#### 4.2. 기간 범위 정의

- `periodType = "ALL"`
  - 기간 필터 적용하지 않음.

- `periodType = "WEEK"`
  - **이번 주의 시작 ~ 끝** 범위만 포함.
  - 기준:
    - 주 시작: 일반적으로 **월요일** 기준 (백엔드/도메인 규칙에 맞게 결정)
    - 예) `2026-03-12(목)` 기준
      - 주 시작: `2026-03-09(월)`
      - 주 끝: `2026-03-15(일)`
  - 조건 예:
    - `workoutDate BETWEEN weekStart AND weekEnd`

- `periodType = "MONTH"`
  - **이번 달 1일 ~ 말일** 범위만 포함.
  - 예) `2026-03-12` 기준
    - 월 시작: `2026-03-01`
    - 월 끝: `2026-03-31`
  - 조건 예:
    - `workoutDate BETWEEN monthStart AND monthEnd`

#### 4.3. 정렬 및 페이징

- 정렬:
  - 기본: `workoutDate DESC`, `createdAt DESC` 등 최신 운동이 위로 오도록 정렬.
  - 프론트는 이 정렬 순서에 의존해 단순히 페이지를 이어붙입니다.
- 페이징:
  - JPA/Pageable 또는 직접 LIMIT/OFFSET 방식으로 구현 시:
    - WHERE 절에 `periodType`에 따른 날짜 범위를 추가한 뒤, **그 결과에 대해 페이지네이션**.
  - 즉, 기간 필터 → 정렬 → 페이징 순서로 계산.

---

### 5. 예외/엣지 케이스 처리

- `periodType` 값이 지정되지 않았거나 `"ALL"`인 경우:
  - 기존 구현과 동일하게 전체 기간 대상으로 동작.
- `periodType`가 `"ALL" | "WEEK" | "MONTH"` 이외의 값인 경우:
  - 옵션 1: 400 Bad Request 반환 (권장)
  - 옵션 2: `"ALL"`로 강제 처리 (짧은 기간 내 임시 대응용)
- 해당 기간에 운동 인증이 하나도 없는 경우:
  - `content: []`, `last: true`, `totalPages: 0`, `number: 0` 등의 형태로 응답.
  - 프론트는 "아직 운동 인증이 없습니다." UI를 그대로 사용.

---

### 6. 구현 포인트 요약

- [ ] `/api/members/workout-feed` 컨트롤러/핸들러에 `periodType` 쿼리 파라미터 추가
  - 타입: enum 또는 `String` → enum 변환
- [ ] 서비스 계층에서 `periodType`에 따라 날짜 범위 계산
  - `ALL`: 필터 없음
  - `WEEK`: 이번 주 시작/끝 계산
  - `MONTH`: 이번 달 시작/끝 계산
- [ ] 레포지토리(쿼리)에서:
  - 회원 ID + (선택적으로) 날짜 범위를 조건으로 조회
  - `Pageable` 인자로 `page`, `size`, 정렬 기준 적용
- [ ] 응답은 기존과 동일한 `Page<WorkoutFeedItem>` → `PageResponse<WorkoutFeedItem>` 형태로 변환
- [ ] 단위/통합 테스트
  - `periodType`별로:
    - 다른 주/다른 달 데이터가 섞여 들어오지 않는지 확인
    - `last` 플래그 및 `totalPages` 계산이 정상인지 확인
