# 프론트엔드 API 개발 가이드

현재 인증 서버 API를 프론트엔드에서 어떻게 확인하고, 어떤 시퀀스로 붙이고, 어떤 방식으로 개발하면 되는지 정리한 문서입니다.

## 1) API는 어디서 확인하나요?

### 실시간 스펙(OpenAPI)
- Redoc: `/docs/index.html`
- Swagger: `/docs/swagger/index.html`
- 원본 yaml: `/docs/openapi3.yaml`

예시(로컬):
- `http://localhost:8080/docs/index.html`
- `http://localhost:8080/docs/swagger/index.html`

### 프론트 요약 문서
- [core/core-api/src/main/resources/static/docs/frontend-api-guide.md](../core/core-api/src/main/resources/static/docs/frontend-api-guide.md)

### 호출 예시 파일
- [core/core-api/src/test/http/local_auth.http](../core/core-api/src/test/http/local_auth.http)
- [core/core-api/src/test/http/admin_auth.http](../core/core-api/src/test/http/admin_auth.http)
- [core/core-api/src/test/http/README.md](../core/core-api/src/test/http/README.md)

## 2) API 카테고리

- `Health`: 상태 점검
- `Local Auth`: 로그인/회원가입/토큰 재발급/로그아웃/탈퇴
- `Local Phone`: 휴대폰 인증번호 발급/확인
- `OAuth2`: 소셜 로그인 시작 URL 조회
- `Admin Audit`: 관리자 로그인 이력
- `Admin Users`: 관리자 사용자 조회/수정/삭제/보안
- `Admin User Status`: 차단/해제/상태 이력
- `Admin SMS`: 문자 발송/이력/통계

## 3) 사용자 인증 시퀀스

### A. 이메일 회원가입 + 로그인
1. `POST /api/v1/auth/local/phone-verifications/request`
2. `POST /api/v1/auth/local/phone-verifications/confirm`
3. `POST /api/v1/auth/local/signup`
4. `POST /api/v1/auth/local/login`
5. accessToken은 메모리 보관, refreshToken은 보안 저장소 보관

### B. 로그인 상태 유지(앱 재진입)
1. refreshToken 존재 확인
2. `POST /api/v1/auth/local/token/refresh` 호출 (`X-Device-Id` 필수)
3. 성공 시 새 accessToken/refreshToken 교체
4. 실패(401 등) 시 로그인 화면으로 이동

### C. 로그아웃
1. `POST /api/v1/auth/local/logout` 호출 (`Authorization`, `X-Device-Id`)
2. 로컬 토큰/세션 상태 삭제

### D. 회원 탈퇴
1. `POST /api/v1/auth/local/withdraw` 호출
2. 성공 시 로컬 상태 초기화 후 온보딩/로그인 화면 이동

## 4) 소셜 로그인(OAuth2) 시퀀스

1. `GET /api/v1/auth/oauth2/{provider}/authorize-url?returnUrl=/your/callback`
2. 응답 문자열 경로로 브라우저 이동
3. 서버 OAuth2 로그인 완료 후 returnUrl로 복귀
4. 프론트는 로그인 완료 상태를 확인하고 초기 데이터 로드

## 5) 관리자 기능 시퀀스

### 사용자 관리 화면 진입
1. `GET /api/v1/auth/admin/users`
2. 상세 모달 시 `GET /api/v1/auth/admin/users/{userId}`

### 상태 관리
1. 차단: `POST /api/v1/auth/admin/users/{userId}/block`
2. 차단 해제: `POST /api/v1/auth/admin/users/{userId}/unblock`
3. 상태 이력: `GET /api/v1/auth/admin/users/{userId}/status-audits`

### 보안 운영
1. 비밀번호 재설정: `POST /api/v1/auth/admin/users/{userId}/password/reset`
2. 토큰 무효화: `POST /api/v1/auth/admin/users/{userId}/tokens/revoke`

## 6) 프론트 구현 규칙

### 공통 헤더
- 사용자 API: `Authorization: Bearer {accessToken}`
- 관리자 API: `Authorization: Bearer {adminAccessToken}`
- 토큰 재발급/로그아웃: `X-Device-Id` 필수

### 에러 처리
- `401`: 토큰 만료/무효 -> refresh 시도 후 실패 시 로그인 이동
- `403`: 권한 없음 -> 접근 차단 페이지
- `400`: 필드 검증 실패 -> 폼 필드 메시지 매핑

### 토큰 처리
- accessToken: 메모리(상태관리)
- refreshToken: 보안 저장소(HTTP-only 쿠키 또는 안전한 스토리지 전략)
- 동시 요청 401 폭주 방지: refresh 단일 비행(single flight) 처리

## 7) 개발 순서 권장

1. `/docs/openapi3.yaml` 기준 타입 생성(openapi generator 등)
2. API 클라이언트 레이어 작성
3. 인증 상태 저장소(auth store) 작성
4. 로그인/토큰재발급 인터셉터 작성
5. 화면별 시퀀스 연결(Local -> OAuth2 -> Admin)
6. `core/core-api/src/test/http/*.http`로 서버 동작 교차 검증

## 8) 체크리스트

- 모든 요청/응답 DTO가 OpenAPI와 일치하는가
- `X-Device-Id` 필요한 API에 누락이 없는가
- 401 refresh 재시도 로직이 무한루프가 아닌가
- 관리자 화면에서 403 대응이 되어 있는가
- 로그아웃/탈퇴 후 토큰이 완전히 제거되는가
