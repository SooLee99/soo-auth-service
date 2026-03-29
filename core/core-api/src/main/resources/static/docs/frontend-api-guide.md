# 인증 서비스 API 가이드 (Frontend)

프론트엔드에서 바로 붙일 수 있도록 카테고리별로 전체 API를 정리한 문서입니다.

## 카테고리 맵
- Health: 상태 점검
- Local Auth: 로그인/회원가입/토큰/로그아웃/탈퇴
- Local Phone: 휴대폰 인증번호 발급/확인
- OAuth2: 소셜 로그인 시작 URL
- Admin Audit: 관리자 로그인 이력
- Admin Users: 관리자 사용자 조회/수정/보안/삭제
- Admin User Status: 관리자 차단/해제/상태 이력/목록
- Admin SMS: 관리자 문자 운영

## 1) 공통 규칙

### Base URL
- `http://localhost:8080`

### 공통 응답 포맷 (`ApiResponse`)
```json
{
  "result": "SUCCESS",
  "meta": {
    "timestamp": "2026-03-28T12:00:00",
    "request": {
      "path": "/api/v1/auth/local/token/refresh",
      "method": "POST",
      "query": null
    }
  },
  "data": {},
  "error": null
}
```

### 인증 헤더
- 사용자 인증: `Authorization: Bearer {accessToken}`
- 관리자 인증: `Authorization: Bearer {adminAccessToken}`
- 디바이스 식별(필요 API): `X-Device-Id: {deviceId}`

### 공통 에러
- `401` 인증 실패/만료
- `403` 권한 없음
- `400` 요청 검증 실패

---

## 2) Health

### `GET /health`
- 인증: 불필요
- 설명: 공개 헬스체크

### `GET /api/v1/auth/admin/health`
- 인증: 관리자 토큰 필요 (`ROLE_ADMIN`)
- 설명: 관리자 상세 헬스체크

---

## 3) Local Auth

### `POST /api/v1/auth/local/login`
- 인증: 불필요
- 헤더: `X-Device-Id` 권장
- 요청:
```json
{
  "email": "user@example.com",
  "password": "P@ssw0rd!"
}
```
- 응답 `data`:
```json
{
  "accessToken": "...",
  "accessExpiresInSec": 1800,
  "refreshToken": "...",
  "refreshExpiresInSec": 1209600
}
```
- 비고: 컨트롤러가 아닌 `LocalJsonLoginFilter`에서 처리

### `POST /api/v1/auth/local/signup`
- 인증: 불필요
- 요청 필드:
  - `email`, `password`, `gender`, `phoneNumber`, `phoneVerificationToken` 필수
  - 선택: `name`, `nickname`, `locale`, `profileImageUrl`, `thumbnailImageUrl`, `birthyear`, `birthday`
- 응답: `200 OK` (본문 없음)

### `POST /api/v1/auth/local/signup/phone`
- 인증: 불필요
- 요청:
```json
{
  "phoneNumber": "+821012345678",
  "phoneVerificationToken": "..."
}
```
- 응답: `200 OK` (본문 없음)

### `POST /api/v1/auth/local/token/refresh`
- 인증: 불필요
- 헤더: `X-Device-Id` 필수
- 요청:
```json
{
  "refreshToken": "..."
}
```
- 응답 `data`: `IssuedTokens` (로그인 응답과 동일 구조)

### `POST /api/v1/auth/local/logout`
- 인증: 사용자 토큰 필요
- 헤더: `X-Device-Id` 필수
- 요청(선택):
```json
{
  "refreshToken": "...",
  "logoutAll": false
}
```
- 응답 `data`:
```json
{
  "result": "OK"
}
```

### `POST /api/v1/auth/local/withdraw`
- 인증: 사용자 토큰 필요
- 요청(선택):
```json
{
  "reason": "privacy"
}
```
- 응답 `data`:
```json
{
  "result": "OK"
}
```

---

## 4) Local Phone

### `POST /api/v1/auth/local/phone-verifications/request`
- 인증: 불필요
- 요청:
```json
{
  "phoneNumber": "+821012345678"
}
```
- 응답 `data`:
```json
{
  "verificationId": "...",
  "expiresInSec": 180
}
```

### `POST /api/v1/auth/local/phone-verifications/confirm`
- 인증: 불필요
- 요청:
```json
{
  "phoneNumber": "+821012345678",
  "verificationId": "...",
  "code": "123456"
}
```
- 응답 `data`:
```json
{
  "phoneVerificationToken": "...",
  "expiresInSec": 600
}
```

---

## 5) OAuth2

### `GET /api/v1/auth/oauth2/{provider}/authorize-url`
- 인증: 불필요
- Query: `returnUrl`(선택, 반드시 `/`로 시작)
- Header: `X-Device-Id`(선택)
- 응답 `data`: `"/oauth2/authorization/{provider}"`

## 6) Admin Audit

Path prefix: `/api/v1/auth/admin`

모든 API 인증 필요: 관리자 토큰 (`ROLE_ADMIN`)

- `GET /login-history`
  - Query: `page`, `size`, `startDate`, `endDate`

---

## 7) Admin Users

Path prefix: `/api/v1/auth/admin`

모든 API 인증 필요: 관리자 토큰 (`ROLE_ADMIN`)

### 사용자 조회
- `GET /users`
  - Query: `page`, `size`, `keyword`, `userStatus`, `role`, `authProvider`
- `GET /users/{userId}`

### 사용자 상태 변경
- `PATCH /users/{userId}` (`AdminUserUpdateRequest`)
- `POST /users/{userId}/delete` (`reason` 선택)
- `POST /users/{userId}/password/reset` (`newPassword` 필수)
- `POST /users/{userId}/tokens/revoke`

---

## 8) Admin User Status

Path prefix: `/api/v1/auth/admin`

모든 API 인증 필요: 관리자 토큰 (`ROLE_ADMIN`)

- `POST /users/{userId}/block` (`reason` 선택)
- `POST /users/{userId}/unblock`
- `GET /users/blocked` (Query: `page`, `size`)
- `GET /users/deleted` (Query: `page`, `size`)
- `GET /users/{userId}/status-audits` (Query: `page`, `size`)

---

## 9) Admin SMS

Path prefix: `/api/v1/auth/admin/sms`

모든 API 인증 필요: 관리자 토큰 (`ROLE_ADMIN`)

### `POST /send`
- 요청:
```json
{
  "to": "01012345678",
  "text": "[ADMIN] 공지 메시지"
}
```
- 응답 `data`:
```json
{
  "id": 1,
  "to": "01012345678",
  "from": "029302266",
  "text": "[ADMIN] 공지 메시지",
  "ok": true,
  "provider": "SOLAPI",
  "at": "2026-03-28T13:00:00"
}
```

### `GET /logs`
- Query: `page`, `size`, `startDate`, `endDate`
- 응답: 문자 발송 이력 페이지

### `GET /stats`
- Query: `startDate`, `endDate`
- 응답 `data`:
```json
{
  "total": 100,
  "ok": 95,
  "fail": 5,
  "rate": 95.0
}
```

---

## 10) 프론트 테스트용 HTTP 파일

- `core/core-api/src/test/http/local_auth.http`
- `core/core-api/src/test/http/admin_auth.http`
- `core/core-api/src/test/http/README.md`

위 파일을 기준으로 실제 호출 순서대로 검증하면 됩니다.
