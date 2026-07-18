# BE-HCM

> "헬창모임" — 그룹 운동 습관 관리 앱의 **Spring Boot 3.5 / Java 21 백엔드 API 서버**입니다. 사용자는 소규모
> 그룹인 "운동방"에 참여해 매일 운동 인증을 올리고, 실시간으로 채팅하며, 약속을 지키지 못하면 벌금을 냅니다.
> 그 외에 한국투자증권 주식 시세 연동과 FCM 푸시 알림 기능도 포함되어 있습니다.

프론트엔드 저장소는 [`FE-HCM`](../FE-HCM)이며, REST(`{ success, message, data }` 응답 포맷) + WebSocket(STOMP)으로
연동됩니다.

## 목차

- [기술 스택](#기술-스택)
- [핵심 기능](#핵심-기능)
- [프로젝트 구조](#프로젝트-구조)
- [사전 요구 사항](#사전-요구-사항)
- [환경 설정](#환경-설정)
- [실행 방법](#실행-방법)
- [배포 및 파이프라인 (CI/CD)](#배포-및-파이프라인-cicd)

---

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| 언어 / 런타임 | ![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white) (Gradle Toolchain으로 고정, 런타임 이미지는 Amazon Corretto 21 alpine) |
| 프레임워크 | ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.3-6DB33F?logo=springboot&logoColor=white) |
| 빌드 도구 | ![Gradle](https://img.shields.io/badge/Gradle-Groovy%20DSL-02303A?logo=gradle&logoColor=white) (Gradle Wrapper 포함) |
| 데이터 접근 | Spring Data JPA(Hibernate) — `default_batch_fetch_size: 500`, `open-in-view: false`, `ddl-auto: update` |
| 데이터베이스 | ![MySQL](https://img.shields.io/badge/MySQL-8.4-4479A1?logo=mysql&logoColor=white) (dev/prod, `mysql-connector-j`), 로컬은 `mariadb-java-client`로 접속 |
| 캐시 | ![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)(`spring-boot-starter-data-redis`) + Spring Cache + Caffeine 3.1.8 |
| 인증/보안 | Spring Security(Stateless JWT), `io.jsonwebtoken:jjwt` 0.11.5, OAuth2 Client(Google / Kakao / Naver 소셜 로그인) |
| 실시간 통신 | `spring-boot-starter-websocket` + `spring-messaging` (STOMP, `/wss`, SockJS 폴백) |
| 이메일 | `spring-boot-starter-mail` (회원가입 이메일 인증) |
| 외부 연동 | Firebase Admin SDK 9.2.0(FCM 푸시), 한국투자증권 Open API(주식 시세, `RestTemplateConfig`/`httpclient5`), Spring Cloud AWS 2.2.6(S3) |
| API 문서화 | springdoc-openapi-starter-webmvc-ui 2.2.0 (Swagger UI: `/swagger-ui.html`, OpenAPI: `/v3/api-docs`) |
| 비밀값 암호화 | Jasypt(`jasypt-spring-boot-starter` 3.0.5) — YAML에 `ENC(...)` 형식으로 암호화 저장 |
| 컨테이너 | ![Docker](https://img.shields.io/badge/Docker-amazoncorretto:21--alpine--jdk-2496ED?logo=docker&logoColor=white) |
| CI/CD | GitHub Actions → Docker Hub → EC2 SSH 배포(`docker-compose`) |

## 핵심 기능

- **회원/인증**: 이메일 인증 기반 자체 회원가입·로그인과 Google/Kakao/Naver OAuth2 소셜 로그인을 모두
  지원하며, 두 방식 모두 로그인 성공 시 동일하게 JWT(access/refresh)를 발급합니다.
- **운동방(그룹) 관리**: `WorkoutRoom`(그룹) 생성/참여, `WorkoutRoomMember`(멤버십) 관리.
- **일일 운동 인증**: `WorkoutRecord`로 매일의 운동 인증(이미지 포함)을 기록합니다.
- **주간 마감 및 벌금 계산**: `WorkoutSchedulingService`가 매주 월요일 자정(`0 0 0 * * MON`)에 `@Scheduled`
  크론 잡으로 실행되어 주간 마감 및 벌금 계산을 트리거합니다. `Penalty` / `PenaltyAccount` 엔티티로 벌금
  부과·납부 이력을 추적합니다.
- **실시간 채팅**: STOMP 엔드포인트(`/wss`, SockJS 폴백)로 운동방 단위 실시간 채팅을 제공하며, HTTP 인증과
  별개로 `JwtChannelInterceptor`가 WebSocket 채널의 JWT 인증을 담당합니다.
- **푸시 알림**: Firebase Admin SDK로 FCM 토큰 기반 푸시 알림을 발송합니다.
- **통계/랜딩 데이터**: `/api/stats/**`는 인증 없이(`permitAll`) 접근 가능한 통계/랜딩 페이지용 집계 API를
  제공합니다.
- **주식 시세 연동**: 한국투자증권 Open API를 통해 시세 데이터를 조회합니다.
- **관리자 기능**: `/api/admin/**` 하위에 `ROLE_ADMIN` 권한이 필요한 회원/운동방 관리 API를 제공합니다.
- **파일 업로드**: AWS S3(Spring Cloud AWS)를 통해 운동 인증/채팅/프로필 이미지를 업로드합니다.

## 프로젝트 구조

```
BE-HCM/
├── src/main/java/com/behcm/
│   ├── domain/                        # 도메인 주도 구조 (레이어 우선 아님)
│   │   ├── auth/                        # 회원가입/로그인, JWT 발급, OAuth2(Google/Kakao/Naver), 이메일 인증
│   │   ├── member/                      # 회원 프로필/계정
│   │   ├── workout/                     # WorkoutRoom, WorkoutRoomMember, WorkoutRecord, 주간 마감 스케줄러
│   │   ├── penalty/                     # Penalty / PenaltyAccount (벌금 추적/납부)
│   │   ├── chat/                        # WebSocket/STOMP 실시간 방 채팅
│   │   ├── notification/                # FCM 푸시 알림
│   │   ├── stats/                       # 통계/랜딩 페이지용 집계 데이터 (permitAll)
│   │   ├── stock/                       # 한국투자증권 API 주식/시세 연동
│   │   ├── rest/                        # 기타 REST 엔드포인트
│   │   ├── admin/                       # 관리자 전용 API (admin/member, admin/workout 등, ROLE_ADMIN)
│   │   └── common/                      # 도메인 간 공유 요소 (HealthCheckController 등)
│   │       # 각 도메인 모듈: controller/ → service/ → repository/ (+ dto/, entity/)
│   └── global/
│       ├── config/                      # SecurityConfig, RedisConfig, CacheConfig(Caffeine), AsyncConfig,
│       │                                # RestTemplateConfig, JasyptConfig, FcmConfig
│       │   ├── aws/                     # S3 설정
│       │   ├── stock/                   # 한국투자증권 클라이언트 설정
│       │   ├── swagger/                 # OpenAPI/Springdoc 설정
│       │   └── websocket/               # STOMP 브로커 + JWT 채널 인터셉터
│       ├── security/                    # JWT provider/filter/entry point, OAuth2 성공/실패 핸들러
│       ├── exception/                   # CustomException, ErrorCode(도메인별 그룹화), GlobalExceptionHandler
│       └── common/                      # ApiResponse<T>, BaseTimeEntity(생성/수정 시각 감사)
├── src/main/resources/
│   ├── application.yml                  # 공통 설정 (Jasypt, FCM, Swagger, mail, OAuth2 등)
│   ├── application-local.yml            # local 프로필 (MariaDB/Redis localhost)
│   ├── application-dev.yml              # dev 프로필
│   ├── application-prod.yml             # prod 프로필
│   └── application-secret.yml           # Jasypt로 암호화된 시크릿 (spring.profiles.include: secret)
├── src/test/resources/                  # 테스트 전용 설정 (application-local.yml: localhost:3306 root/1234, localhost:6379)
├── .github/workflows/deploy.yml         # CI(빌드/테스트) + CD(Docker 빌드/푸시 + EC2 배포)
├── Dockerfile                           # amazoncorretto:21-alpine-jdk 런타임 이미지
├── build.gradle
├── settings.gradle
└── gradlew / gradlew.bat
```

## 사전 요구 사항

| 도구 | 버전 | 비고 |
| --- | --- | --- |
| Java (JDK) | 21 | Gradle Toolchain으로 자동 지정 (`build.gradle`) |
| Docker / Docker Compose | 최신 버전 | 로컬 MySQL/Redis 실행 또는 컨테이너 빌드 시 필요 |
| MySQL 또는 MariaDB | 8.x / 호환 버전 | 로컬 프로필은 `mariadb-java-client`로 `localhost:3306` 접속 |
| Redis | 7.x | 캐시 용도 |

> Gradle은 저장소에 포함된 Wrapper(`./gradlew`)를 사용하므로 별도 설치가 필요 없습니다.

## 환경 설정

Spring 프로필은 `spring.profiles.active`로 선택하며 기본값은 `local`입니다(`application.yml`). 사용 가능한
프로필: `local`, `dev`, `prod`. `application-secret.yml`은 `spring.profiles.include: secret`로 자동
포함되며, 안에 담긴 값은 모두 Jasypt로 암호화(`ENC(...)`)되어 있어 실제 비밀값이 커밋되어 있지는 않습니다.

로컬 실행 시 필요한 환경 변수:

```bash
export JASYPT_ENCRYPTOR_PASSWORD=[이곳에 정보 입력]     # application-secret.yml 복호화 키 (필수)
export FCM_KEY_PATH=[이곳에 정보 입력]                  # 기본값: /app/config/fcm-key.json (Firebase 서비스 계정 키 경로)
```

`application.yml` / `application-{profile}.yml`의 주요 키:

| 키 | 설명 |
| --- | --- |
| `server.port` | API 서버 포트 (기본 `8080`) |
| `spring.profiles.active` | 사용할 프로필 (`local` / `dev` / `prod`) |
| `spring.profiles.include: secret` | `application-secret.yml`(암호화된 값) 자동 포함 |
| `spring.datasource.*` | DB 접속 정보 — local: `jdbc:mariadb://localhost:3306/hcm?...`, `root`/`1234`; dev/prod는 암호화됨 |
| `spring.jpa.hibernate.ddl-auto` | local/dev/prod 모두 `update` |
| `spring.data.redis.*` | Redis 접속 정보 — local: `localhost:6379`; dev/prod는 암호화(호스트/포트/비밀번호) |
| `spring.mail.*` | 이메일 인증 발송용 SMTP 설정 (host/username/password 모두 암호화) |
| `spring.security.oauth2.client.registration.{google,kakao,naver}.*` | 소셜 로그인 클라이언트 ID/Secret (암호화) |
| `cloud.aws.credentials.*`, `cloud.aws.s3.bucket` | S3 업로드용 AWS 자격 증명 및 버킷 (암호화, 리전은 `ap-northeast-2` 고정) |
| `jasypt.encryptor.password` | `${JASYPT_ENCRYPTOR_PASSWORD}` 환경 변수 참조 |
| `fcm.key-path` | Firebase Admin SDK 서비스 계정 키 파일 경로 (`${FCM_KEY_PATH:/app/config/fcm-key.json}`) |
| `app.frontend-url` | OAuth2 로그인 성공 후 리다이렉트할 프론트엔드 URL (local: `http://localhost:3000`, prod: `https://www.bellwin.co.kr`) |
| `springdoc.swagger-ui.path` | Swagger UI 경로 (`/swagger-ui.html`) |

> `local` 프로필은 저장소 루트의 `docker-compose.yml`로 띄운 MySQL(root/1234)·Redis에 바로 접속 가능하도록
> 구성되어 있어, 별도 시크릿 없이도 `JASYPT_ENCRYPTOR_PASSWORD`만 설정하면 로컬 구동이 가능합니다. `dev`/`prod`
> 프로필의 DB·Redis·AWS·메일·OAuth2 값은 모두 암호화되어 있어 저장소만으로는 실제 값을 알 수 없습니다.

## 실행 방법

### 1) 로컬 인프라(DB/Redis) 기동

저장소 루트(`HCM/`)의 `docker-compose.yml`로 MySQL 8.4 + Redis 7을 먼저 띄웁니다.

```bash
cd ..    # HCM 루트로 이동
docker compose up -d
```

### 2) 로컬 빌드 및 실행

```bash
export JASYPT_ENCRYPTOR_PASSWORD=[이곳에 정보 입력]

./gradlew build              # 전체 빌드 (컴파일 + 테스트 실행)
./gradlew bootRun            # 로컬 실행 (기본적으로 local 프로필 사용, http://localhost:8080)
```

주요 Gradle 명령어:

```bash
./gradlew build -x test      # 테스트 없이 빌드 (CI에서 사용하는 방식과 동일한 스킵 옵션)
./gradlew test               # 전체 테스트 실행
./gradlew test --tests "com.behcm.domain.workout.service.WorkoutRoomServiceTest"             # 특정 테스트 클래스만 실행
./gradlew test --tests "com.behcm.domain.workout.service.WorkoutRoomServiceTest.methodName"  # 특정 테스트 메서드만 실행
```

별도로 설정된 lint 태스크는 없으며, `./gradlew build`(컴파일 + 테스트)로 코드 정합성을 검증합니다.

정상 기동 후 확인 가능한 주소:

- API: `http://localhost:8080/api`
- Health check: `http://localhost:8080/api/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

### 3) Docker 기반 실행

이미지 빌드 전 먼저 `./gradlew build`로 `build/libs/*.jar`를 생성해야 합니다.

```bash
./gradlew build

docker build -t hcm-backend .
docker run -d \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local \
  -e JASYPT_ENCRYPTOR_PASSWORD=[이곳에 정보 입력] \
  --name hcm-backend \
  hcm-backend
```

> `Dockerfile`의 기본 `SPRING_PROFILES_ACTIVE`는 `prod`입니다. 로컬에서 컨테이너로 실행할 경우 위처럼
> `local`로 재정의하고, 컨테이너가 호스트의 `localhost:3306`/`localhost:6379`에 접속할 수 있도록 네트워크
> 설정(`--network host` 등)이 필요합니다.

## 배포 및 파이프라인 (CI/CD)

`.github/workflows/deploy.yml` — `main`, `dev` 브랜치에 대한 push/PR 시 다음 파이프라인이 실행됩니다.

1. **CI**
   - JDK 21(Temurin) 설정, Gradle 캐싱(`~/.gradle/caches`, `~/.gradle/wrapper`)
   - `mysql:8.4`, `redis:7-alpine` 서비스 컨테이너를 띄운 상태에서 `./gradlew clean build --no-daemon` 실행
     (`@SpringBootTest`가 `src/test/resources/application-local.yml`의 로컬 인프라 구성을 기대하므로, CI에서도
     동일하게 서비스 컨테이너로 구성).
   - 테스트 실패 시 테스트 리포트(`build/reports/tests/test`)를 아티팩트로 업로드.
   - Docker Hub 로그인 후, `main` 브랜치는 `latest` 태그, 그 외 브랜치는 `dev` 태그(+ 커밋 SHA 태그)로 이미지를
     빌드/푸시(PR 이벤트에서는 push 생략, GHA 캐시 사용).
2. **CD** (`ci` Job 성공 + PR 이벤트가 아닐 때만 실행)
   - `main`이면 `production`, 그 외에는 `development` GitHub Environment의 시크릿을 사용.
   - `appleboy/ssh-action`으로 EC2에 SSH 접속 → 기존 `hcm` 컨테이너 중지/정리(exited 컨테이너 prune 포함) →
     신규 이미지 pull → `sudo docker-compose -f $DOCKER_COMPOSE_PATH up -d` 실행.

필요한 GitHub Secrets(값은 저장소에서 알 수 없음): `DOCKER_REPO`, `DOCKER_USERNAME`, `DOCKER_PASSWORD`,
`EC2_HOST`, `EC2_USERNAME`, `EC2_SSH_KEY`, `DOCKER_COMPOSE_PATH` — `production` / `development` 두
GitHub Environment에 각각 별도로 설정됩니다(Secret 이름은 동일하지만 값은 환경별로 다름).

## GitHub 워크플로우 관례

- 브랜치명: `feature/issue-{issue_number}` 또는 `fix/issue-{issue_number}`
- 커밋 메시지: 한국어로 작성하고 `(#{issue_number})`로 끝맺음
- PR base 브랜치: `dev`를 거치지 않고 항상 `main`으로 바로 병합
- 이슈/PR의 `title`, `body`는 한국어로 작성 (자세한 절차는 `.cursor/commands/github-issue-pr-command.md` 참고)
