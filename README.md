# soo-auth-service

인증 서버(로컬/소셜 로그인, 관리자 사용자 관리, SMS 관리) 프로젝트입니다.

## 문서
- 문서 전체 인덱스: [docs/README.md](docs/README.md)
- 사용자 가이드 모음: [docs/guides/README.md](docs/guides/README.md)
- API/개발 문서 모음: [docs/api/README.md](docs/api/README.md)
- 로컬 HTTP 테스트 가이드: [core/core-api/src/test/http/README.md](core/core-api/src/test/http/README.md)
- 배포 문서 모음: [docs/deploy/README.md](docs/deploy/README.md)
- 모니터링 문서 모음: [docs/monitoring/README.md](docs/monitoring/README.md)
- 장애/이슈 보고서 모음: [docs/issues/README.md](docs/issues/README.md)

---
## 모듈 구성

### Core
- 이 모듈의 각 하위 모듈은 하나의 도메인 서비스를 담당합니다.
- 서비스의 성장에 맞춰 모듈 구조도 함께 확장해야 합니다.
- `core:core-app`
  - 실행 모듈입니다. 인증 모듈(`email/phone/id/oauth2`)을 조합해 애플리케이션을 구성합니다.
- `core:core-api`
  - 공통 API/Security/Local 인증 코어와 공통 도메인을 제공합니다.
  - 인증 체계별 모듈(`core-email`, `core-phone`, `core-id`, `core-oauth2`)이 이 모듈을 기반으로 동작합니다.
- `core:core-email`
  - 이메일 기반 로컬 회원가입 API를 담당합니다.
- `core:core-phone`
  - 휴대폰 기반 로컬 가입/로그인 API를 담당합니다.
- `core:core-id`
  - ID 기반 로컬 가입/로그인 API를 담당합니다.
- `core:core-oauth2`
  - OAuth2 로그인/프로필 파싱/후처리를 담당합니다.
- `core:core-enum`
  - 이 모듈은 `core-api`에서 사용되며 외부 모듈로 전달해야 하는 열거형을 포함하고 있습니다.

### Clients
- 이 모듈의 하위 모듈은 외부 시스템과의 통합을 담당합니다.
- `clients:client-example` (`clients:clients-example` 표기의 기존 예시와 동일한 대상 모듈)
  - 이 모듈은 Spring-Cloud-Open-Feign을 사용한 HTTP 통신 예시를 제공합니다.

### Storage
- 이 모듈의 하위 모듈은 다양한 저장소와의 통합을 담당합니다.
- `storage:db-core`
  - 이 모듈은 Spring-Data-JPA를 사용하여 MySql에 연결하는 예시를 제공합니다.

### Support
- 이 모듈의 하위 모듈은 추가적인 지원을 담당합니다.
- `support:logging`
  - 이 모듈은 서비스의 로깅을 지원하며, 분산 추적을 위한 종속성을 추가로 제공합니다.
  - 또한 Sentry를 지원하는 종속성도 포함하고 있습니다.
- `support:monitoring`
  - 이 모듈은 서비스 모니터링을 지원합니다.

### Tests
- 이 모듈의 하위 모듈은 테스트 코드를 작성하는 데 편리함을 제공합니다.
- `tests:api-docs`
  - 이 모듈은 spring-rest-docs를 편리하게 작성할 수 있도록 지원합니다.
---
## 코드 작성 규칙(요약)

1. Controller는 요청/응답 변환과 인증 정보 추출만 담당
2. Business 흐름은 각 도메인 모듈의 `domain/*`에서 오케스트레이션
3. 상세 구현은 구현 레이어로 분리하고 재사용 가능한 단위로 작성
4. 저장소 접근은 `storage:db-core`로 격리
5. 레이어 참조는 상위 -> 하위 단방향 유지 
6. 클래스명은 대상(도메인/역할/책임)을 나타낸다. 
7. 메서드명은 해당 클래스 맥락에서 수행하는 동작을 나타낸다. 
8. 클래스가 대상을 충분히 설명하는 경우, 메서드명은 `list`, `create`, `update`처럼 행위 중심으로 작성할 수 있다. 
9. 단, 의미가 모호해지는 경우에는 `getById`, `listActive`, `createAdmin`, `reissueToken`처럼 조건/목적/대상을 보강한다. 
10. 저장소 계층은 `findBy...`, `existsBy...`, `save`, `deleteBy...` 등 조회/저장 의도가 드러나는 이름을 사용한다. 
11. 구현 레이어는 재사용 가능한 역할이 드러나도록 `UserFinder`, `UserAppender`, `SmsSender`와 같이 작성한다.
12. DDD 기준으로 애그리게이트 경계를 넘는 상태 변경은 각 애그리게이트 루트를 통해 수행한다.
13. 엔티티의 상태 변경 규칙/불변식은 엔티티 메서드에 캡슐화하고, 서비스에서 필드 `copy`를 남발하지 않는다.
14. Domain Service는 유스케이스 오케스트레이션과 트랜잭션 경계에 집중하고, 엔티티가 표현 가능한 규칙은 엔티티에 위임한다.
15. 외부 시스템/DB/프레임워크 의존 로직은 도메인 모델 밖(Repository/Adapter)으로 분리한다.
16. 도메인 용어(유비쿼터스 언어)를 클래스/메서드명에 일관되게 반영한다.
---
## 종속성 관리

- 모든 종속성 버전 관리는 `gradle.properties` 파일을 통해 수행됩니다.
- 새로운 종속성을 추가하려면 `gradle.properties`에 버전을 추가하고, 이를 `build.gradle`에서 로드하면 됩니다.
---
## 실행 프로필

- `local`: 로컬 독립 개발
- `local-dev`: 로컬에서 DEV 연동
- `dev`: 개발 서버 배포
- `staging`: 스테이징 배포
- `live`: 운영 배포

---
## 인증 체계 토글

- 인증 체계별로 API 동작을 비활성화할 수 있습니다.
- 기본값은 모두 `true` 입니다.

```yaml
app:
  auth:
    features:
      email: true
      phone: true
      id: true
      oauth2: true
```

- 환경변수:
  - `AUTH_FEATURE_EMAIL`
  - `AUTH_FEATURE_PHONE`
  - `AUTH_FEATURE_ID`
  - `AUTH_FEATURE_OAUTH2`

- 예시(Email/OAuth2만 활성화):

```bash
AUTH_FEATURE_EMAIL=true \
AUTH_FEATURE_PHONE=false \
AUTH_FEATURE_ID=false \
AUTH_FEATURE_OAUTH2=true \
./gradlew :core:core-app:bootRun
```

- 빌드 시 인증 모듈 조합을 바꾸려면 아래 속성을 사용합니다(기본 `true`).
  - `-Pauth.module.email.enabled`
  - `-Pauth.module.phone.enabled`
  - `-Pauth.module.id.enabled`
  - `-Pauth.module.oauth2.enabled`

- 예시(Email + ID만 포함):

```bash
./gradlew :core:core-app:compileKotlin \
  -Pauth.module.phone.enabled=false \
  -Pauth.module.oauth2.enabled=false
```
---
## 테스트 작업/태그

- `test`
  - 이 작업은 CI에서 실행하고 싶은 테스트 작업들의 모음입니다.
  - 설정을 변경하려면 `build.gradle` 파일을 수정하십시오.
- `unitTest`
  - 이 작업은 일반적으로 의존성이 없고, 빠르게 실행되며 단일 기능을 테스트하는 테스트들입니다.
- `contextTest`
  - 이 작업은 SpringContext와 함께 실행되며, 통합 테스트를 수행합니다.
- `restDocsTest`
  - 이 작업은 spring-rest-docs를 기반으로 asciidoc을 생성하는 작업입니다.
- `developTest`
  - 이 작업은 CI에서 실행되지 않아야 하는 테스트 작업입니다.
  - 테스트 작성에 익숙하지 않다면 이 태그를 사용하는 것이 좋습니다.
---

## 문서 재생성
```bash
./gradlew :core:core-api:generateApiDocs
```

---
## 환경 독립 테스트/검증

- 테스트는 로컬 환경변수(`.env`, `SPRING_PROFILES_ACTIVE`)에 영향받지 않도록 Gradle에서 `local` 프로필로 강제됩니다.
- CI/로컬 공통 권장 검증 순서:

```bash
./gradlew clean
./gradlew :core:core-api:compileKotlin :core:core-api:testClasses
./gradlew :storage:db-core:compileKotlin
./gradlew ktlintCheck
./gradlew build
./gradlew :core:core-api:generateApiDocs
```

---
## 권장 설정

### Git Hook
- 이 설정은 커밋 시마다 lint를 실행하도록 설정합니다.

```bash
git config core.hookspath .githooks
```

### IntelliJ IDEA
- 이 설정은 test code를 바로 실행할 수 있도록 설정합니다.

```text
// IntelliJ IDEA에서 Gradle 빌드 및 실행
Build, Execution, Deployment > Build Tools > Gradle > Run tests using > IntelliJ IDEA
```

- IntelliJ IDEA의 포맷에 lint 설정을 적용하려면 아래 가이드를 참조하십시오.
- Spring Java Format IntelliJ IDEA: https://github.com/spring-io/spring-javaformat#intellij-idea
---
