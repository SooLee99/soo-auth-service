# Auth API Flow Guide

이 문서는 현재 코드 기준 인증 API 흐름 요약입니다. (최신화: 2026-04-13)

## 1. 전체 구조

- Controller: 요청/응답 매핑, 헤더/인증 정보 추출
- Domain: 각 인증 모듈(`core-email`, `core-id`, `core-sms`, `core-oauth2`, `core-admin`)의 유스케이스 오케스트레이션
- Common Domain: `core-user-common`, `core-login-common`, `core-phone-common`, `core-token-common`, `core-support`
- Storage: `storage/db-core`

참조 방향은 `Controller -> Domain -> Storage` 단방향입니다.

## 2. 최신 API 엔드포인트

### 2.1 Health
- `GET /health`
- `GET /api/v1/auth/admin/health`

### 2.2 Local Auth
- `POST /api/v1/auth/local/login` (Spring Security 필터)
- `POST /api/v1/auth/local/email/signup`
- `POST /api/v1/auth/local/id/signup`
- `POST /api/v1/auth/local/id/login`
- `POST /api/v1/auth/local/phone/signup`
- `POST /api/v1/auth/local/phone/login`
- `POST /api/v1/auth/local/phone-verifications/request`
- `POST /api/v1/auth/local/phone-verifications/confirm`
- `POST /api/v1/auth/local/token/refresh`
- `POST /api/v1/auth/local/logout`
- `POST /api/v1/auth/local/withdraw`
- `GET /api/v1/auth/local/session`

### 2.3 OAuth2
- `GET /api/v1/auth/oauth2/{provider}/authorize-url`

### 2.4 Admin
- `POST /api/v1/auth/admin/login` (Spring Security 필터)
- `GET /api/v1/auth/admin/login-history`
- `GET /api/v1/auth/admin/users`
- `GET /api/v1/auth/admin/users/{userId}`
- `PATCH /api/v1/auth/admin/users/{userId}`
- `POST /api/v1/auth/admin/users/{userId}/password/reset`
- `POST /api/v1/auth/admin/users/{userId}/delete`
- `POST /api/v1/auth/admin/users/{userId}/tokens/revoke`
- `GET /api/v1/auth/admin/users/blocked`
- `POST /api/v1/auth/admin/users/{userId}/block`
- `POST /api/v1/auth/admin/users/{userId}/unblock`
- `GET /api/v1/auth/admin/users/deleted`
- `GET /api/v1/auth/admin/users/{userId}/status-audits`
- `GET /api/v1/auth/admin/auth-methods`
- `PATCH /api/v1/auth/admin/auth-methods/{method}`
- `POST /api/v1/auth/admin/sms/send`
- `GET /api/v1/auth/admin/sms/logs`
- `GET /api/v1/auth/admin/sms/stats`

## 3. 인증 방식별 핵심 흐름

### 3.1 이메일 가입
1. `LocalEmailController.signup`
2. `LocalAccountService.signUp`
3. `UserUniquenessPolicy` 검증
4. `UserRepository` / `LocalCredentialRepository` 저장

### 3.2 아이디 가입/로그인
1. `LocalIdController.signup` / `LocalIdController.login`
2. `IdAccountService` / `IdLoginService`
3. 전화번호 검증/중복 검증/로그인 실패 정책 적용
4. 성공 시 토큰 발급 및 로그인 이력 저장

### 3.3 휴대폰 가입/로그인
1. `LocalPhoneVerificationController`에서 인증번호 발급/확인
2. `LocalPhoneController.signup` / `LocalPhoneController.login`
3. `LocalPhoneAccountService` / `LocalPhoneLoginService`

### 3.4 공통 로컬
- 토큰 재발급: `LocalTokenController.refresh`
- 로그아웃: `LocalTokenController.logout`
- 탈퇴: `LocalUserController.withdraw`
- 세션 상태: `LocalSessionController.getSession`

### 3.5 OAuth2
- 인가 URL 생성: `OAuth2AccountController.authorizeUrl`
- 콜백 성공 후: `OAuth2LoginSuccessHandler`

## 4. 문서 확인 경로

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

참고:
- 현재 저장소에는 정적 `openapi3.yaml` 파일이 없습니다.
- 기존 `:core:core-api:generateApiDocs` 태스크는 현재 미사용입니다.
