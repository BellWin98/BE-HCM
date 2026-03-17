# Landing 페이지 통계 API 백엔드 작업 정리

## 1. 개요

- 랜딩 페이지의 `SocialProofSection`에서 다음 세 가지 지표를 **실제 DB 데이터 기반**으로 노출합니다.
  - 가입 유저 수 (`totalUsers`)
  - 총 운동 인증 횟수 (`totalExerciseProofs`)
  - 현재 운영 중인 방 수 (`activeRooms`)
- 프론트엔드에서는 `GET /stats/landing-summary` 엔드포인트를 호출하도록 구현되어 있으며, **백엔드는 아래 스펙을 만족하는 API를 제공**해야 합니다.

프론트 기준 구현 위치:

- API 모듈: `src/api/stats.ts`
- UI 컴포넌트: `src/components/landing/SocialProofSection.tsx`

---

## 2. 엔드포인트 스펙

- **Method**: `GET`
- **URL**: `/api/stats/landing-summary`
  - FE 공통 클라이언트의 `baseURL`은 `VITE_API_BASE_URL` 환경 변수 기준 (`http://localhost:8080/api` 기본값)
  - 따라서 서버 기준 실제 엔드포인트는 `/api/stats/landing-summary` 입니다.

### 2.1. 요청(Request)

- **Query parameter / Request body 없음**
- 인증 여부:
  - 현재 FE에서는 공통 `api` 인스턴스를 사용하고 있어, **로그인 여부와 무관하게 접근 가능한 public 엔드포인트**로 설계하는 것을 권장합니다.
  - 만약 인증이 필요하다면, 토큰이 없는 경우에도 401이 아니라 **익명 접근용 기본 값을 내려주는 별도 정책**이 필요합니다(랜딩 페이지 노출용이기 때문).

### 2.2. 응답(Response)

프론트에서 기대하는 타입 (`LandingStats`) 은 다음과 같습니다:

```ts
type LandingStats = {
  totalUsers: number; // 가입된 전체 유저 수
  totalExerciseProofs: number; // 전체 운동 인증(업로드) 횟수
  activeRooms: number; // 현재 운영(활성) 중인 운동방 개수
};
```

백엔드 응답 JSON 예시:

```json
{
  "totalUsers": 137,
  "totalExerciseProofs": 623,
  "activeRooms": 7
}
```

---

## 3. 프론트엔드 동작 요약 (백엔드 참고용)

### 3.1. 데이터 로딩 흐름

- `SocialProofSection` 마운트 시, `getLandingStats()` 호출
- 성공 시:
  - 상태에 그대로 저장 후 화면에 반영
- 실패 시:
  - 에러 플래그(`isError`)를 세우고, **프론트에서 정의한 fallback 값**으로 대체 렌더링

관련 코드 구조(요약):

```ts
// src/api/stats.ts
export const getLandingStats = async (): Promise<LandingStats> => {
  const response = await api.request<LandingStatsApiResponse>(
    "/stats/landing-summary",
    {
      method: "GET",
    },
  );

  return {
    totalUsers: response.totalUsers,
    totalExerciseProofs: response.totalExerciseProofs,
    activeRooms: response.activeRooms,
  };
};
```

```ts
// src/components/landing/SocialProofSection.tsx (요약)
const FALLBACK_STATS: LandingStats = {
  totalUsers: 13,
  totalExerciseProofs: 600,
  activeRooms: 6,
};

// ...생략...
const displayStats = stats ?? FALLBACK_STATS;
```

### 3.2. UI에서의 값 사용 방식

- **상단 문구**
  - `"현재 {displayStats.totalUsers.toLocaleString()}명의 유저가 꾸준히 운동을 인증하고 있어요!"`
- **좌측 카드**
  - 타이틀: `총 운동 인증 횟수`
  - 값: `displayStats.totalExerciseProofs` (카운트업 애니메이션)
- **우측 카드**
  - 타이틀: `현재 운영 중인 방`
  - 값: `displayStats.activeRooms` (카운트업 애니메이션)

---

## 4. 백엔드 구현 가이드

### 4.1. totalUsers

- 의미: 서비스에 가입한 전체 유저 수
- 추천 집계 방식:
  - `members` 혹은 `users` 테이블 기준 `WHERE deleted = false` 등 활성 유저만 집계
  - 예: `SELECT COUNT(*) FROM members WHERE deleted = false;`

### 4.2. totalExerciseProofs

- 의미: 사용자가 올린 모든 운동 인증(업로드) 수의 총합
- 추천 집계 방식:
  - 운동 인증을 저장하는 테이블 예: `workouts`, `workout_proofs` 등
  - 예: `SELECT COUNT(*) FROM workouts WHERE deleted = false;`
  - 만약 방 별로 종속된 구조라면 조인 없이 가장 단순한 기준으로 전체 카운트 집계

### 4.3. activeRooms

- 의미: 현재 운영(활성) 중인 운동방의 개수
- 추천 집계 방식:
  - 방 상태 컬럼(예: `status`, `active`, `endDate` 등)을 활용
  - 예시 기준:
    - `status = 'ACTIVE'` 이거나
    - 현재 날짜가 `startDate <= NOW() <= endDate` 인 방만 집계
  - 예:
    - `SELECT COUNT(*) FROM workout_rooms WHERE active = true;`

---

## 5. 에러 및 성능 고려사항

- 이 엔드포인트는 **랜딩 페이지에서만 사용**되며, 트래픽이 많을 수 있음.
- 권장 사항:
  - 단순한 `COUNT(*)` 집계지만, 테이블이 매우 커질 경우를 대비해서 **캐싱**(예: 1분 단위 메모리 캐시, Redis 등) 고려
  - 장애 시에도 프론트는 fallback 값을 사용하므로, **5xx 대신 200 + 기본 값**을 내려주는 전략도 가능 (팀 정책에 따라 결정)
  - 현재 FE는 에러 발생 시 fallback 값을 사용하므로, 일반적인 5xx/4xx 응답도 처리 가능

---

## 6. 요약

- `GET /api/stats/landing-summary` 엔드포인트를 추가하고, 아래 필드를 JSON으로 반환합니다.
  - `totalUsers: number`
  - `totalExerciseProofs: number`
  - `activeRooms: number`
- FE는 이 데이터를 사용해:
  - 상단 문구(가입 유저 수)
  - 두 개의 카드(총 운동 인증 횟수, 현재 운영 중인 방)
    를 카운트업 애니메이션과 함께 렌더링합니다.
