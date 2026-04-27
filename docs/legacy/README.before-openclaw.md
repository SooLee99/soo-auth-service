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
  - 현재 소스 없는 placeholder 모듈(실행/도메인 책임 없음)

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
./gradlew :core:core-auth-common:bootRun
./gradlew :core:core-auth-common:compileKotlin
./gradlew :core:core-email:compileKotlin :core:core-id:compileKotlin :core:core-sms:compileKotlin :core:core-oauth2:compileKotlin :core:core-admin:compileKotlin
./gradlew :core:core-api:testClasses
./gradlew ktlintCheck
```

## API 문서 확인

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

주의:
- 기존 `:core:core-api:generateApiDocs` 태스크는 현재 프로젝트에 없습니다.
- 정적 OpenAPI 산출물 대신 springdoc 런타임 문서를 사용합니다.

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
