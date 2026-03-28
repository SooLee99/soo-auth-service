# HTTP API Test Guide

이 디렉터리의 `.http` 파일로 현재 API를 바로 테스트할 수 있습니다.

## Files

- `local_auth.http`
  - 로컬 인증 API
  - 휴대폰 인증번호 발급/확인
  - 회원가입(이메일/전화번호)
  - 토큰 갱신/로그아웃/탈퇴
  - OAuth2 인가 URL 조회
  - `/health`

- `service_auth.http`
  - 서비스 스코프 인증 API (`/api/v1/services/{serviceCode}/...`)
  - 서비스별 휴대폰 인증번호 발급/확인
  - 서비스별 회원가입/로그인/토큰 갱신/로그아웃/탈퇴
  - 서비스별 OAuth2 인가 URL 조회

- `admin_auth.http`
  - 관리자 API
  - 사용자 관리(조회/수정/차단/해제/삭제/비밀번호 초기화/토큰 철회)
  - 서비스 관리(등록/조회/비활성화)
  - 로그인 이력 조회
  - 관리자 SMS 발송/이력/통계
  - `/api/v1/auth/admin/health`

## Quick Start

1. 각 파일 상단 변수 설정
   - `@baseUrl`
   - `@serviceCode`
   - `@deviceId`
   - `@adminAccessToken` 또는 `@accessToken`

2. 권장 실행 순서
   - 일반 사용자 흐름: `local_auth.http`
   - 서비스 사용자 흐름: `service_auth.http`
   - 관리자 기능 검증: `admin_auth.http`

3. 변수 자동 저장
   - 일부 요청 하단의 `> {% ... %}` 스크립트가
   - `verificationId`, `phoneVerificationToken`, `accessToken`, `refreshToken`을 자동 저장합니다.

## Admin SMS API

- 발송: `POST /api/v1/auth/admin/sms/send`
- 이력: `GET /api/v1/auth/admin/sms/logs`
- 통계: `GET /api/v1/auth/admin/sms/stats`
- 기간 필터: `startDate`, `endDate` (ISO-8601)
  - 예: `2026-03-01T00:00:00`

## Notes

- 관리자 API는 `ROLE_ADMIN` 권한 토큰이 필요합니다.
- 서비스 API는 유효한 `serviceCode`가 필요합니다.
- OpenAPI(`static/docs/openapi3.yaml`)는 RestDocs 기반 산출물이며, 테스트 산출 시점에 따라 최신 구현과 일부 차이가 있을 수 있습니다.
