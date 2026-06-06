# soo-auth-service

인증 서버(로컬/소셜 로그인, 관리자 사용자 관리, SMS 관리) 프로젝트입니다.

## 문서
- 문서 전체 인덱스: [docs/README.md](docs/README.md)
- 사용자 가이드 모음: [docs/guides/README.md](docs/guides/README.md)
- API/개발 문서 모음: [docs/api/README.md](docs/api/README.md)
- 배포 문서 모음: [docs/deploy/README.md](docs/deploy/README.md)
- 모니터링 문서 모음: [docs/monitoring/README.md](docs/monitoring/README.md)
- 장애/이슈 보고서 모음: [docs/issues/README.md](docs/issues/README.md)

---
## 모듈 구성 (2026-04-13 기준)

### Core
- `core:core-auth-common`
  - 실행 진입점(`CoreApiApplication`), 공통 보안/설정, Health, 인증방식 토글 공통 도메인
- `core:core-admin`
  - 관리자 API(사용자 관리, SMS 관리, 인증 방식 on/off)
- `core:core-email`
  - 이메일 기반 회원가입/공통 로컬 API(세션/토큰/로그아웃/탈퇴)
- `core:core-id`
  - 아이디 기반 회원가입/로그인
- `core:core-sms`
  - 휴대폰 인증/휴대폰 기반 로그인 및 SMS
- `core:core-oauth2`
  - OAuth2 인가 URL/로그인 처리
  - Kakao JS SDK accessToken → 자체 JWT 교환 SDK-토큰 흐름 (`POST /api/v1/auth/oauth2/kakao/token`)
- `core:core-support`
  - 공통 에러/응답 타입
- `core:core-token-common`
  - 토큰/리프레시/denylist 도메인
- `core:core-user-common`
  - 사용자 상태/생명주기/중복 정책
- `core:core-login-common`
  - 로그인 시도 정책/로그인 이력
- `core:core-phone-common`
  - 휴대폰 인증/정규화 공통 도메인
- `core:core-enum`
  - 여러 모듈에서 공통으로 사용하는 enum/type
- `core:core-api`
  - 애플리케이션 실행 Aggregator (bootJar 생성, 모든 core 모듈 통합)

### Clients
- `clients:client-solapi`
  - SOLAPI 클라이언트 연동

### Storage
- `storage:db-core`
  - Spring Data JPA(MySQL) 저장소 계층

### Support
- `support:logging`
- `support:monitoring`

### Tests
- `tests:api-docs`
  - RestDocs/OpenAPI 스니펫 테스트 유틸

---
## 코드 작성 규칙(요약)

1. Controller는 요청/응답 변환과 인증 정보 추출만 담당
2. Business 흐름은 각 도메인 모듈의 `domain/*`에서 오케스트레이션
3. 상세 구현은 구현 레이어로 분리하고 재사용 가능한 단위로 작성
4. 저장소 접근은 `storage:db-core`로 격리
5. 레이어 참조는 상위 -> 하위 단방향 유지
6. 클래스명은 대상(도메인/역할/책임)을 나타낸다
7. 메서드명은 해당 클래스 맥락에서 수행 동작을 나타낸다
8. 클래스가 충분히 대상을 설명하면 메서드명은 `list`, `create`, `update`처럼 행위 중심으로 작성 가능
9. 모호하면 `getById`, `listActive`, `createAdmin`, `reissueToken`처럼 조건/목적/대상을 보강
10. 저장소 계층은 `findBy...`, `existsBy...`, `save`, `deleteBy...` 등 의도가 드러나는 이름 사용
11. 구현 레이어는 `UserFinder`, `UserAppender`, `SmsSender`처럼 재사용 역할이 드러나게 작성
12. 애그리게이트 경계를 넘는 상태 변경은 루트 엔티티를 통해 수행
13. 상태 변경 규칙/불변식은 엔티티 메서드에 캡슐화
14. Domain Service는 유스케이스 오케스트레이션/트랜잭션 경계에 집중
15. 외부 시스템/DB/프레임워크 의존 로직은 도메인 모델 밖(Repository/Adapter)으로 분리
16. 도메인 용어를 클래스/메서드명에 일관 반영

---
## 종속성 관리

- 종속성 버전은 `gradle.properties`에서 관리합니다.

---
## 실행 프로필

- `local`: 로컬 독립 개발
- `local-dev`: 로컬에서 DEV 연동
- `dev`: 개발 서버 배포
- `staging`: 스테이징 배포
- `live`: 운영 배포

---
## 테스트 작업/태그

- `test`: CI 대상 테스트 묶음
- `unitTest`: 빠른 단위 테스트
- `contextTest`: SpringContext 통합 테스트
- `restDocsTest`: RestDocs 생성 테스트
- `developTest`: CI 제외 개발용 테스트

---
## 로컬 실행/검증

```bash
./gradlew :core:core-api:bootRun
./gradlew :core:core-api:compileKotlin
./gradlew ktlintCheck
```

---
## Docker 배포 및 운영 가이드

이 프로젝트는 Docker Hub 이미지를 기반으로, **소스 코드 없이도** 운영 서버를 즉시 구축할 수 있도록 설계되었습니다.

### 1. 운영 핵심 요약
- **코드 없는 배포**: 운영 서버에는 오케스트레이션 파일(Compose, Nginx)만 있으면 됩니다.
- **단일 이미지, 다중 컨테이너**: 하나의 이미지를 환경 변수에 따라 다른 역할의 컨테이너로 동작시킵니다.
- **선택적 활성화**: 필요한 인증 방식만 실행하여 서버 자원(CPU, Memory)을 최적화합니다.

### 2. 운영 서버 빠른 시작 (One-liner)
운영 서버에서 아래 명령어로 필요한 파일만 가져와 즉시 실행할 수 있습니다. (자세한 내용은 [운영 가이드](docs/deploy/README_OPERATIONS_GUIDE.md) 참조)

```bash
# 1. 배포에 필요한 파일만 가져오기
git clone --depth 1 --filter=blob:none --sparse https://github.com/your-repo/soo-auth-service.git
cd soo-auth-service && git sparse-checkout set deploy nginx docker-compose.yml .env.example

# 2. 실행 (시나리오별 선택)
./deploy/deploy.sh --core --web --common --oauth2 --admin # 예: OAuth2 + 관리자
```

> **주의 (Mac 사용자)**: 실행 시 `failed to connect to the docker API` 에러가 나면 Docker Desktop 앱이 실행 중인지 확인하고, 터미널에서 `docker context use default` 명령을 실행해 보세요.

### 3. 주요 실행 시나리오 (복사용)
| 시나리오 | 실행 명령어 |
| :--- | :--- |
| **이메일 로그인** | `./deploy/deploy.sh --core --web --common --email --admin` |
| **ID/PW 로그인** | `./deploy/deploy.sh --core --web --common --id --admin` |
| **SMS 로그인** | `./deploy/deploy.sh --core --web --common --sms --admin` |
| **OAuth2 로그인** | `./deploy/deploy.sh --core --web --common --oauth2 --admin` |
| **로컬 개발 모드** | `./deploy/deploy.sh --core --web --common --oauth2 --admin --dev` |

---
## 4. 로컬 접속 방법 (Local Quick Start)

로컬 개발 환경에서 도커로 실행 중인 서비스에 접속하는 방법입니다.

### 4.1 로컬 hosts 파일 설정 (필수)
브라우저에서 `soo.it.kr` 도메인으로 접근하기 위해 로컬 PC의 `hosts` 파일을 수정해야 합니다.

*   **파일 위치**:
    *   Mac/Linux: `/etc/hosts` (명령어: `sudo nano /etc/hosts`)
    *   Windows: `C:\Windows\System32\drivers\etc\hosts` (메모장을 관리자 권한으로 실행)
*   **추가 내용**:
    ```text
    127.0.0.1  soo.it.kr
    ```

### 4.2 주요 서비스 URL 및 포트
모든 서비스는 Nginx를 통해 **HTTPS(443)** 포트로 통합 관리됩니다.

| 서비스 | 접속 주소 (URL) | 설명 |
| :--- | :--- | :--- |
| **관리자 UI** | [https://soo.it.kr/](https://soo.it.kr/) | 사용자 및 시스템 관리 화면 |
| **API 문서** | [https://soo.it.kr/docs/](https://soo.it.kr/docs/) | Swagger UI (API 명세서) |
| **DB 관리** | [https://soo.it.kr/phpmyadmin/](https://soo.it.kr/phpmyadmin/) | MariaDB 웹 관리도구 (`--tools` 필요) |
| **메트릭** | [https://soo.it.kr/prometheus](https://soo.it.kr/prometheus) | 시스템 성능 지표 확인 |

### 4.3 브라우저 보안 경고 해결
자체 서명(Self-signed) 인증서를 사용하므로 최초 접속 시 보안 경고가 발생합니다.
1.  브라우저에서 "연결이 비공개로 설정되어 있지 않습니다" 경고 확인
2.  **'고급'** 버튼 클릭
3.  **'soo.it.kr(으)로 이동(안전하지 않음)'** 링크 클릭

### 4.4 포트 및 내부 서비스 정보
모든 외부 요청은 Nginx(80, 443)를 통해 각 내부 서비스로 전달됩니다.

| 구분 | 포트 (Port) | 접근 주소 | 비고 |
| :--- | :--- | :--- | :--- |
| **HTTP** | `80` | `http://127.0.0.1` | HTTPS(443)로 자동 리다이렉트 |
| **HTTPS** | `443` | `https://127.0.0.1` | **메인 서비스 접속 포트** |
| **API 서버 (컨테이너)** | `8080` | `http://app-*:8080` | 컨테이너 내부 통신용 (외부 노출 X) |
| **API 서버 (로컬 네이티브)** | `18080` | `http://localhost:18080` | OPS-1 포트 분리. travel-service(8080)와 동시 기동 충돌 방지. `.env.local`의 `SERVER_PORT`로 주입 |
| **MariaDB** | `3306` | `mariadb:3306` | 컨테이너 내부 통신용 (외부 노출 X) |
| **Redis** | `6379` | `redis:6379` | 컨테이너 내부 통신용 (외부 노출 X) |

> **Tip**: 외부 DB 도구(DBeaver, Sequel Pro 등)로 DB에 직접 접속하려면 `./deploy/deploy.sh` 실행 시 `--dev` 옵션을 사용하거나, `docker-compose.yml`의 `mariadb` 서비스에 `ports: - "3306:3306"` 설정을 추가해야 합니다.

---
## 5. 로컬 개발자 모드 (Local Dev Mode)

개발 편의를 위해 DB 포트를 외부에 노출하고 `localhost` 도메인 접속을 지원하는 전용 모드를 제공합니다.

### 5.1 실행 방법
기존 명령어에 `--dev` 플래그를 추가하면 됩니다.

```bash
./deploy/deploy.sh --core --web --common --oauth2 --admin --dev
```

### 5.2 주요 접속 URL
개발 모드에서는 `hosts` 파일 수정 없이 아래 주소로 즉시 접속 가능합니다.

| 서비스 | 접속 주소 (URL) | 비고 |
| :--- | :--- | :--- |
| **관리자 UI** | [https://localhost/](https://localhost/) | |
| **API 문서** | [https://localhost/docs/](https://localhost/docs/) | |
| **DB 관리** | [https://localhost/phpmyadmin/](https://localhost/phpmyadmin/) | `--tools` 필요 |

### 5.3 개발 모드 특징
- **Nginx 설정**: `localhost` 및 `127.0.0.1` 호스트명을 지원합니다. (`hosts` 파일 수정 없이 `https://localhost` 접속 가능)
- **DB 포트 노출**: MariaDB(3306) 및 Redis(6379) 포트가 로컬 호스트로 노출됩니다.
- **백엔드 프로파일**: Spring Boot가 `local-dev` 프로파일로 실행됩니다. (관리자 계정 자동 생성 등 개발 편의 기능 활성화)

---
운영 및 배포에 관한 더 자세한 내용은 아래 문서를 참고하십시오.
- **[운영 가이드 (Docker Hub 기반)](docs/deploy/README_OPERATIONS_GUIDE.md)**: 이미지 빌드, 푸시 및 운영 서버 구축 상세 가이드
- **[도커 로그 확인 및 관리 가이드](docs/deploy/README_DOCKER_LOGS.md)**: 컨테이너별 로그 확인 및 트러블슈팅 방법
- [서비스 분리 및 프로파일 가이드](docs/deploy/README.md): Docker Compose Profiles를 이용한 자원 최적화 원리

---

## API 문서 (Swagger)

- Swagger UI: `http://localhost:18080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:18080/v3/api-docs`

주의:
- 정적 OpenAPI 산출물 대신 springdoc 런타임 문서를 사용합니다.
- 로컬 네이티브 기동 포트는 `18080`입니다(OPS-1: triplan-travel-service가 8080을 쓰므로 충돌 방지차 분리). `.env.local`의 `SERVER_PORT=18080`로 주입되며, 프론트 `VITE_AUTH_BASE_URL=http://localhost:18080/api/v1`와 정합. 컨테이너 내부 통신 포트는 아래 4.4 표대로 `8080` 유지.

---
## 권장 설정

### Git Hook

```bash
git config core.hookspath .githooks
```

### IntelliJ IDEA

```text
Build, Execution, Deployment > Build Tools > Gradle > Run tests using > IntelliJ IDEA
```

- Spring Java Format IntelliJ 가이드: https://github.com/spring-io/spring-javaformat#intellij-idea
