# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

BE-HCM("헬창모임")은 그룹 운동 습관 관리 앱을 위한 Spring Boot 4.1 / Java 25 백엔드입니다. 사용자는 소규모
그룹인 "운동방"에 참여해 매일 운동 인증을 올리고, 실시간으로 채팅하며, 약속을 지키지 못하면 벌금을 냅니다.
그 외에 한국투자증권 주식 시세 연동과 FCM 푸시 알림 기능도 포함되어 있습니다.

## 명령어

```bash
./gradlew build              # 전체 빌드(컴파일 + 테스트 실행)
./gradlew build -x test      # 테스트 없이 빌드(CI에서 사용)
./gradlew test               # 전체 테스트 실행
./gradlew test --tests "com.behcm.domain.workout.service.WorkoutRoomServiceTest"      # 특정 테스트 클래스만 실행
./gradlew test --tests "com.behcm.domain.workout.service.WorkoutRoomServiceTest.methodName"  # 특정 테스트 메서드만 실행
./gradlew bootRun            # 로컬 실행(기본적으로 `local` Spring 프로필 사용)
```

별도로 설정된 lint 태스크는 없으며, `./gradlew build`(컴파일 + 테스트)로 코드 정합성을 검증합니다.

## 테스트 작성 원칙 (TDD)

신규 기능을 개발할 때는 RED-GREEN-REFACTOR 방식을 따릅니다:

1. **RED** — 구현에 앞서 실패하는 테스트를 먼저 작성합니다.
2. **GREEN** — 테스트를 통과시키는 최소한의 코드를 작성합니다.
3. **REFACTOR** — 테스트가 계속 통과하는 상태를 유지하면서 코드를 정리합니다.

버그 수정도 동일하게 적용합니다 — 버그를 재현하는 실패 테스트를 먼저 작성한 뒤 수정하세요. 커밋 전에는
관련 테스트(`./gradlew test --tests "..."`)와 전체 빌드(`./gradlew build`)로 검증합니다.

## 설정 및 프로필

- Spring 프로필은 `spring.profiles.active`로 선택합니다(`application.yml`의 기본값은 `local`). 사용 가능한
  프로필: `application-local.yml`, `application-dev.yml`, `application-prod.yml`. `application-secret.yml`은
  `spring.profiles.include: secret`로 자동 포함되며 암호화된 값들을 담고 있습니다 — 실제 비밀값이 커밋되어
  있지는 않습니다.
- YAML 내 비밀값은 Jasypt로 암호화되어 있으며(`ENC(...)`), 부팅 시 `JASYPT_ENCRYPTOR_PASSWORD` 환경변수로
  복호화됩니다(`global/config/JasyptConfig.java`).
- Docker 이미지(`Dockerfile`)는 기본적으로 `SPRING_PROFILES_ACTIVE=prod`이며, `build/libs/*.jar` 경로의
  빌드 산출물을 기대합니다.
- CI/CD는 GitHub Actions(`.github/workflows/deploy.yml`)로 구성되어 있습니다: `main`/`dev` 브랜치에 push되면
  Gradle로 빌드(테스트는 스킵)한 뒤 Docker 이미지를 빌드/푸시하고(`main`은 `latest` 태그, 그 외는 `dev` 태그),
  해당 GitHub Environment(`production` / `development`)의 시크릿을 사용해 EC2에 SSH로 접속해
  `docker-compose up -d`를 실행합니다.

## DB 스키마 변경 (Flyway)

스키마는 Flyway가 단독으로 관리합니다. 모든 프로필에서 `ddl-auto: validate`이므로 **Hibernate는 스키마를
절대 변경하지 않고 검증만 합니다** — 엔티티에 컬럼을 추가하고 마이그레이션을 빠뜨리면 부팅이 실패합니다.

- 마이그레이션 파일 위치: `src/main/resources/db/migration/V{N}__{설명}.sql`
- `V1__baseline.sql`은 Flyway 도입 시점의 기준 스키마입니다. 기존 dev/prod DB는
  `baseline-on-migrate: true` / `baseline-version: 1` 설정에 의해 V1을 건너뛰고 V2부터 적용받습니다.
  **V1을 포함해 이미 적용된 마이그레이션 파일은 절대 수정하지 마세요** — 체크섬이 어긋나 부팅이 실패합니다.
  변경이 필요하면 항상 새 버전 파일을 추가합니다.
- 스키마를 바꿀 때는 엔티티와 마이그레이션을 **함께** 수정하고, 인덱스/제약조건은 이름을 양쪽에서 동일하게
  유지합니다(`@Table(indexes = ..., uniqueConstraints = ...)`).
- 운영 DB에 대한 DDL은 온라인으로 처리되도록 `ALGORITHM = INPLACE LOCK = NONE`을 명시합니다.
  (`CREATE INDEX`에서는 두 옵션 사이에 쉼표를 쓰지 않고, `ALTER TABLE`에서는 절 구분자로 쉼표를 씁니다.)
- UNIQUE 제약을 새로 추가하는 마이그레이션은 기존 데이터에 중복이 있으면 실패해 앱이 부팅되지 않습니다.
  배포 전에 중복 검사 쿼리를 먼저 돌리세요(해당 마이그레이션 파일 상단 주석에 쿼리를 적어 둡니다).
- **파일 하나에 DDL 한 개**를 원칙으로 합니다. MySQL은 DDL을 롤백하지 않으므로, 여러 DDL을 한 파일에
  묶으면 중간에 실패했을 때 부분 적용 상태가 남아 재실행이 불가능해집니다(재실행 시 앞부분이
  "duplicate key name" 등으로 실패). 실제로 V4가 이 문제를 겪어 V4/V5로 분리했습니다.
- 마이그레이션이 실패한 채로 남으면 이후 모든 마이그레이션이 막힙니다. 실패 기록을 지우려면
  `flyway repair`(또는 `DELETE FROM flyway_schema_history WHERE success = 0`)로 정리한 뒤,
  **DB를 해당 마이그레이션 적용 이전 상태로 되돌리고** 다시 실행해야 합니다.
- **FK 컬럼을 커버하는 인덱스를 새로 추가할 때 `DROP INDEX`로 기존 FK 인덱스를 지우려 하지 마세요.**
  InnoDB가 FK 제약을 위해 자동 생성한 인덱스는 이를 대체할 인덱스가 생기는 순간 MySQL이 스스로
  제거하므로, `DROP INDEX`는 "그런 인덱스 없음"으로 실패합니다. 반대로 V1 baseline으로 만들어진
  신규 DB에는 그 인덱스가 명시적으로 존재해 남아 있습니다 — 즉 같은 DDL이 환경에 따라 다르게
  동작합니다. 중복 인덱스는 그냥 두는 편이 안전합니다.

## 아키텍처

### 패키지 구조 (레이어 우선이 아닌 도메인 주도 구조)

`com.behcm`은 `domain/*`(기능별 모듈)과 `global/*`(공통 인프라)로 나뉩니다. 각 도메인 모듈은 동일하게
`controller/ → service/ → repository/` 계층을 따르며, 필요에 따라 `dto/`, `entity/`를 포함합니다.

- `domain/auth` — 회원가입/로그인, JWT 발급, OAuth2(Google/Kakao/Naver) 소셜 로그인, 이메일 인증.
- `domain/member` — 회원 프로필/계정.
- `domain/workout` — `WorkoutRoom`(그룹), `WorkoutRoomMember`, `WorkoutRecord`(일일 운동 인증). 매주 월요일
  자정(`0 0 0 * * MON`)에 실행되는 `@Scheduled` 크론 잡인 `WorkoutSchedulingService`가 있으며, 주간 마감 및
  벌금 계산을 트리거하는 것으로 보입니다 — 주간 사이클 로직을 수정할 때 가장 먼저 확인해야 할 파일입니다.
- `domain/penalty` — 운동 미이행에 대한 벌금 추적/납부를 위한 `Penalty` / `PenaltyAccount` 엔티티. 운동방
  멤버십과 운동 기록에 의존합니다.
- `domain/chat` — WebSocket/STOMP 기반 실시간 방 채팅.
- `domain/notification` — FCM 푸시 알림.
- `domain/stats` — 통계/랜딩 페이지용 집계 데이터. `SecurityConfig`에서 `/api/stats/**` 엔드포인트는
  `permitAll`로 설정되어 있다는 점에 유의하세요.
- `domain/stock` — 한국투자증권 API를 통한 주식/시세 데이터 연동.
- `domain/rest` — 기타 REST 엔드포인트.
- `domain/admin` — `admin/member`, `admin/workout` 등 관리자 전용 하위 API로, `ROLE_ADMIN` 권한이 필요합니다
  (`SecurityConfig`의 `/api/admin/**`).
- `domain/common` — 도메인 간 공유되는 아주 작은 요소들(예: `/api/health`용 `HealthCheckController`).
- `global/config` — Spring `@Configuration` 클래스 모음: `SecurityConfig`, `RedisConfig`,
  `CacheConfig`(Caffeine), `AsyncConfig`, `RestTemplateConfig`, `JasyptConfig`, `FcmConfig`, 그리고 하위
  패키지로 `aws`(S3), `stock`(한국투자증권 클라이언트), `swagger`(OpenAPI/Springdoc),
  `websocket`(STOMP 브로커 + JWT 채널 인터셉터)이 있습니다.
- `global/security` — JWT provider/filter/entry point 및 OAuth2 성공/실패 핸들러.
- `global/exception` — `CustomException` + `ErrorCode` enum(모든 에러 메시지는 한국어이며, `// Workout Room`,
  `// Chat`처럼 도메인별 주석으로 그룹화되어 있음) + `GlobalExceptionHandler`(`@ControllerAdvice`).
- `global/common` — 컨트롤러가 반환하는 일관된 `{success, message, data}` 형태의 `ApiResponse<T>`와, JPA
  엔티티의 생성/수정 시각 감사(auditing)를 위한 베이스 클래스인 `BaseTimeEntity`.

### 인증 및 보안

- Stateless JWT 인증(`SessionCreationPolicy.STATELESS`)과 함께 OAuth2 로그인(Google/Kakao/Naver)을 지원하며,
  OAuth2 로그인 성공 시에도 `OAuth2AuthenticationSuccessHandler`를 통해 JWT를 발급합니다.
- 인증 없이 접근 가능한(permitAll) 경로: `/api/auth/**`, `/api/oauth2/**`, `/api/stats/**`,
  `/login/oauth2/code/**`, `/api/health`, `/swagger-ui/**`, `/v3/api-docs/**`, `/ws/**`, `/wss/**`.
- `/api/admin/**`은 `ROLE_ADMIN` 권한이 필요하며, 그 외 모든 요청은 인증이 필요합니다.
- CORS는 `localhost:*`와 `https://www.bellwin.co.kr` / `https://dev.bellwin.co.kr`로 제한되며, `/api/**`와
  `/login/oauth2/**` 경로에 적용됩니다.

### 실시간 채팅 (WebSocket/STOMP)

- `/wss` 경로에 STOMP 엔드포인트가 있으며(SockJS 폴백 지원), 메시지 브로커는 `/topic`, 클라이언트→서버
  전송 prefix는 `/app`입니다(`global/config/websocket/WebSocketConfig.java`).
- 들어오는 STOMP 프레임은 `JwtChannelInterceptor`를 통해 인증됩니다(HTTP용 `JwtAuthenticationFilter`와는
  별개로, WebSocket 채널에서 JWT를 검증).

### 따라야 할 컨벤션

- 컨트롤러는 `ApiResponse.success(...)` / `ApiResponse.error(...)`를 통해 `ApiResponse<T>`
  (`global/common/ApiResponse.java`)를 반환합니다 — 새 엔드포인트를 추가할 때도 이 응답 형식을 유지하세요.
- 비즈니스 에러는 `CustomException(ErrorCode.X)` 형태로 던집니다. 새로운 에러 케이스는 새 예외 타입을
  만들지 말고, `ErrorCode`에 해당 도메인 주석 블록 아래에 추가하세요. 에러 메시지는 한국어로 작성합니다.
- 생성/수정 시각이 필요한 엔티티는 `BaseTimeEntity`를 상속합니다.
- `.cursor/rules/back-end.mdc`에 맞춰 DI는 전체적으로 생성자 주입(`@RequiredArgsConstructor`)을 사용합니다.

### Spring Boot 4 관련 주의사항

- **JSON은 Jackson 3(`tools.jackson.*`)를 씁니다.** `com.fasterxml.jackson`은 firebase-admin·jjwt 등이
  전이 의존으로 끌고 와 클래스패스에 남아 있어 그냥 컴파일되지만, 런타임에 자동 구성되는 것은 Jackson 3
  `JsonMapper`뿐이므로 새 코드에서 쓰지 마세요. `JsonNode.asText()`는 `asString()`으로 대체되었습니다.
- **테스트 의존성은 기술별 test 스타터를 씁니다**(`spring-boot-starter-webmvc-test`,
  `spring-boot-starter-security-test`). `spring-boot-starter-test`만으로는 MockMvc(`@AutoConfigureMockMvc`)와
  `@WithMockUser`가 동작하지 않습니다. 각 test 스타터가 `spring-boot-starter-test`를 전이로 가져옵니다.
- **jasypt-spring-boot는 Boot 4를 아직 공식 지원하지 않습니다**(최신 4.0.4도 Boot 3.5 기준 빌드).
  다른 테스트는 평문 값을 쓰기 때문에 복호화 경로를 지나가지 않으므로,
  `JasyptEncryptablePropertyTest`가 `ENC(...)` 복호화를 지키고 있습니다. jasypt나 Boot 버전을 올릴 때
  이 테스트를 반드시 확인하세요 — 깨지면 운영에서 부팅 자체가 실패합니다.
- 자동 구성은 전용 스타터에만 붙습니다. 예를 들어 Flyway는 `org.flywaydb:flyway-core` 직접 선언이 아니라
  `spring-boot-starter-flyway`를 써야 합니다.
