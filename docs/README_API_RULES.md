# API 개발 규칙 및 동작 가이드

이 문서는 이 프로젝트에서 API를 구현/수정할 때 따라야 할 규칙과, 주요 API의 처리 흐름을 정리한 개발자용 README입니다.

## 1. 기본 원칙

1. 멀티서비스 경로 우선
- 신규 API는 `/api/v1/services/{serviceCode}/...` 기준으로 구현합니다.
- 레거시 경로(`/api/v1/auth/...`)는 호환 목적이며 신규 클라이언트에는 권장하지 않습니다.

2. `serviceCode`는 절대 신뢰하지 않음
- 프론트가 보낸 값을 그대로 사용하지 않고, 서버에서 형식/등록/활성 상태를 검증합니다.
- 검증 컴포넌트: `ServiceContextResolver`

3. 삭제보다 상태 전환
- 서비스 제거는 물리 삭제 대신 `INACTIVE` 전환을 사용합니다.
- `DEFAULT` 서비스는 비활성화 금지입니다.

4. 탈퇴는 익명화 포함
- 사용자 탈퇴는 소프트 삭제이며, PII(이메일/전화번호/프로필 등)는 익명화/초기화합니다.

## 2. 코드 작성 규칙

1. Controller 책임
- 요청/응답 스키마 변환
- 인증 principal/헤더 추출
- 도메인 서비스 호출
- 비즈니스 로직은 Controller에 두지 않음

2. Domain Service 책임
- 트랜잭션 경계
- 상태 전이/검증/예외 처리
- 저장소 조합

3. Repository 책임
- 쿼리/영속성 캡슐화
- 도메인 모델 <-> 엔티티 매핑

4. 에러 처리
- `CoreException(ErrorType, data)` 사용
- 사용자 메시지는 `ErrorType` 기준
- 상세 원인은 `data`로 전달

5. 응답 포맷
- 공통 응답은 `ApiResponse.success/error` 사용
- 단순 성공도 `{ "result": "OK" }` 같은 명시적 data 반환 권장

## 3. 서비스 코드 검증 규칙

검증 위치:
- `core/core-api/.../ServiceContextResolver.kt`

검증 내용:
1. 정규식: `^[A-Z0-9][A-Z0-9_-]{1,63}$`
2. DB 등록 여부 확인
3. `ACTIVE` 상태 확인

실패 시:
- 형식 오류: `INVALID_PARAMETER`
- 미등록: `NOT_FOUND`
- 비활성: `SERVICE_INACTIVE`

## 4. 관리자 서비스 관리 규칙

### 4.1 서비스 등록
- API: `POST /api/v1/auth/admin/services`
- 권한: `ROLE_ADMIN`
- 중복 코드면 `DUPLICATE_SERVICE_CODE`

### 4.2 서비스 목록 조회
- API: `GET /api/v1/auth/admin/services`
- 권한: `ROLE_ADMIN`
- 페이징 파라미터: `page`, `size`

### 4.3 서비스 비활성화
- API: `POST /api/v1/auth/admin/services/{serviceCode}/deactivate`
- 권한: `ROLE_ADMIN`
- 보호 규칙:
  1. `DEFAULT` 비활성화 금지
  2. 활성 멤버십 존재 시 비활성화 금지 (`SERVICE_HAS_ACTIVE_MEMBERSHIPS`)

## 5. API별 동작 과정 (멀티서비스 경로)

### 5.1 회원가입
- 경로: `POST /api/v1/services/{serviceCode}/auth/local/signup`
- 흐름:
  1. `serviceCode` 검증(`resolveActive`)
  2. 사용자 중복 검증(활성 계정 기준)
  3. `user_entity` 저장
  4. `local_credential` 저장
  5. (추가 예정) `service_membership` 생성

### 5.2 토큰 갱신
- 경로: `POST /api/v1/services/{serviceCode}/auth/local/token/refresh`
- 흐름:
  1. `serviceCode` 검증
  2. `X-Device-Id` + refresh token으로 rotate
  3. 새 access/refresh 발급
  4. (추가 예정) `service_id` 스코프 일치 검증

### 5.3 로그아웃
- 경로: `POST /api/v1/services/{serviceCode}/auth/local/logout`
- 흐름:
  1. `serviceCode` 검증
  2. access token denylist 등록
  3. refresh token 단건/디바이스/전체 revoke
  4. (추가 예정) 서비스 스코프별 revoke

### 5.4 탈퇴
- 경로: `POST /api/v1/services/{serviceCode}/auth/local/withdraw`
- 흐름:
  1. `serviceCode` 검증
  2. 사용자 상태를 `SOFT_DELETED`로 전환
  3. 개인정보 익명화
  4. 자격증명 삭제 + 토큰 revoke
  5. 상태 감사 로그 저장
  6. (추가 예정) 서비스 탈퇴와 전역 탈퇴 분리

### 5.5 OAuth2 인가 URL 조회
- 경로: `GET /api/v1/services/{serviceCode}/auth/oauth2/{provider}/authorize-url`
- 흐름:
  1. `serviceCode` 검증
  2. `returnUrl` 상대경로 검증
  3. 세션에 `SERVICE_CODE`, `RETURN_URL`, `DEVICE_ID` 저장
  4. `/oauth2/authorization/{provider}` 반환

## 6. 보안 규칙

1. Admin API는 `hasRole("ADMIN")`로 보호
2. 공개 경로는 `ApiSecurityConfig.PUBLIC_ENDPOINTS`에만 추가
3. 새 공개 API를 열 때는 반드시 이유를 문서화
4. 인증 API는 가능하면 `X-Device-Id`를 사용해 디바이스 단위 추적

## 7. 테스트 규칙

필수 테스트 시나리오:
1. 유효하지 않은 `serviceCode` 요청 거부
2. 비활성 서비스 요청 거부
3. 관리자 서비스 등록 중복 거부
4. 활성 멤버십 존재 시 서비스 비활성화 거부
5. 탈퇴 시 익명화 필드 검증

권장 실행:
```bash
./gradlew :storage:db-core:compileKotlin :core:core-api:compileKotlin
./gradlew :core:core-api:test
```

## 8. 주요 파일 맵

- 서비스 코드 검증: `core/core-api/.../ServiceContextResolver.kt`
- 관리자 서비스 관리: `core/core-api/.../AdminServiceManagementService.kt`
- 관리자 서비스 API: `core/core-api/.../AdminServiceController.kt`
- 멀티서비스 로컬 API: `core/core-api/.../ServiceLocalAccountController.kt`
- 멀티서비스 OAuth2 API: `core/core-api/.../ServiceOAuth2AccountController.kt`
- 서비스 저장소: `storage/db-core/.../ServiceRepository*.kt`
- 서비스 멤버십 저장소: `storage/db-core/.../ServiceMembershipRepository*.kt`

