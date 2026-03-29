# API 개발 규칙 및 코드 작성 가이드

이 문서는 현재 코드베이스(단일 인증 도메인 기준)에 맞춘 개발 규칙입니다.

## 1) 현재 API 구조 원칙

- 인증/사용자 API 기본 경로는 아래를 사용합니다.
  - 사용자: `/api/v1/auth/local/*`, `/api/v1/auth/oauth2/*`
  - 관리자: `/api/v1/auth/admin/*`

## 2) 레이어 규칙

기본 레이어:
1. Presentation Layer (`core-api/api/controller`, request/response DTO)
2. Business Layer (`core-api/domain/*`의 유스케이스 오케스트레이션)
3. Implement Layer (`core-api/domain/*`의 세부 구현 서비스/정책)
4. Data Access Layer (`storage/db-core`)

규칙:
1. 레이어 참조는 위 -> 아래 단방향만 허용
2. 참조 역류 금지(하위 레이어가 상위 레이어를 참조하지 않음)
3. 하위 레이어 건너뛰기 금지(Controller -> Repository 직접 접근 금지)
4. 동일 레이어 상호 참조 최소화(Implement Layer만 예외적으로 허용)

## 3) 코드 작성 원칙

### Controller
- 요청 파싱/검증/응답 변환만 담당
- 비즈니스 규칙 구현 금지
- 공통 응답은 `ApiResponse.success/error` 사용

### Domain(Service)
- 유스케이스 흐름 중심으로 작성
- 상세 구현은 하위 협력 객체(정책/리더/라이터/저장소)에 위임
- 메서드명은 동작이 드러나게 작성 (`signUp`, `refreshTokens`, `revokeUserTokens`)

### Repository(Storage)
- 기술 의존성(JPA/Redis) 캡슐화
- 상위 모듈에 구현 기술이 새지 않도록 인터페이스 중심 제공

## 4) 모듈 의존성 원칙

- 상위 모듈은 하위 모듈의 “개념”에 의존하고 구현 기술에 의존하지 않습니다.
- `api` 노출은 꼭 필요한 경우만 사용하고 기본은 `implementation`을 사용합니다.
- 의존성 버전은 `gradle.properties`를 기준으로 관리합니다.

## 5) 보안/인증 규칙

- Admin API: `ROLE_ADMIN` 필수
- 사용자 인증 API:
  - 로그인: `POST /api/v1/auth/local/login` (필터 처리)
  - 토큰 재발급/로그아웃: `X-Device-Id` 필수
- OAuth2 인가 URL:
  - `GET /api/v1/auth/oauth2/{provider}/authorize-url`
  - `returnUrl`은 `/`로 시작하는 상대 경로만 허용

## 6) 테스트/문서 규칙

- `restDocsTest`로 문서 스니펫 생성
- `generateApiDocs`로 OpenAPI 정적 문서 생성
- 문서/코드 변경 시 아래 명령어로 검증:

```bash
./gradlew :core:core-api:compileKotlin :core:core-api:testClasses
./gradlew :core:core-api:generateApiDocs
```

## 7) 체크리스트

- API 경로에 제거된 서비스 스코프가 남아있지 않은가
- Controller에 비즈니스 로직이 들어가 있지 않은가
- Domain이 Storage 구현체를 과도하게 알지 않는가
- 신규 의존성 버전을 `gradle.properties`에서 관리하는가
- RestDocs/OpenAPI가 실제 코드와 일치하는가
