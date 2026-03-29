# soo-auth-service

인증 서버(로컬/소셜 로그인, 관리자 사용자 관리, SMS 관리) 프로젝트입니다.

## 문서
- 프론트엔드 개발 가이드: [docs/README_FRONTEND_API.md](docs/README_FRONTEND_API.md)
- API 개발 규칙/레이어 가이드: [docs/README_API_RULES.md](docs/README_API_RULES.md)
- 로컬 HTTP 테스트 가이드: [core/core-api/src/test/http/README.md](core/core-api/src/test/http/README.md)
- 로깅/모니터링: [docs/README_LOG_MONITORING.md](docs/README_LOG_MONITORING.md)
- 자동화 배포(CI/CD): [docs/README_DEPLOY_AUTOMATION.md](docs/README_DEPLOY_AUTOMATION.md)

## 모듈 구성

### Core
- 이 모듈의 각 하위 모듈은 하나의 도메인 서비스를 담당합니다.
- 서비스의 성장에 맞춰 모듈 구조도 함께 확장해야 합니다.
- `core:core-api`
  - 이 모듈은 프로젝트에서 유일한 실행 가능한 모듈입니다.
  - 초기 개발 생산성을 극대화할 수 있도록 도메인 구조가 설정되어 있습니다.
  - 이 모듈은 또한 API를 제공하고 서비스의 프레임워크 설정을 담당합니다.
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

## 코드 작성 규칙(요약)

1. Controller는 요청/응답 변환과 인증 정보 추출만 담당
2. Business 흐름은 `core-api/domain`에서 오케스트레이션
3. 상세 구현은 구현 레이어로 분리하고 재사용 가능한 단위로 작성
4. 저장소 접근은 `storage:db-core`로 격리
5. 레이어 참조는 상위 -> 하위 단방향 유지
6. 메서드명은 “동작 + 대상”이 드러나게 작성
   - 예: `listUsers`, `revokeUserTokens`, `issueVerification`

## 종속성 관리

- 모든 종속성 버전 관리는 `gradle.properties` 파일을 통해 수행됩니다.
- 새로운 종속성을 추가하려면 `gradle.properties`에 버전을 추가하고, 이를 `build.gradle`에서 로드하면 됩니다.

## 실행 프로필

- `local`: 로컬 독립 개발
- `local-dev`: 로컬에서 DEV 연동
- `dev`: 개발 서버 배포
- `staging`: 스테이징 배포
- `live`: 운영 배포

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

## 신규 서버 배포 가이드 (Docker Compose)

### 1) 서버 준비
```bash
# Ubuntu 기준
sudo apt-get update
sudo apt-get install -y ca-certificates curl git

# Docker 설치
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER

# 재로그인 후 확인
docker --version
docker compose version
```

### 2) 소스 배포 디렉터리 준비
```bash
sudo mkdir -p /opt/soo-auth-service
sudo chown -R $USER:$USER /opt/soo-auth-service
cd /opt/soo-auth-service
git clone <YOUR_REPO_URL> .
```

### 3) 환경 변수 파일(.env) 작성
```bash
cat > .env <<'EOF'
DOCKER_HUB_USERNAME=your-dockerhub-id
```

### 4) 첫 배포
```bash
cd /opt/soo-auth-service
docker compose pull
docker compose up -d --build
```

### 5) 상태 확인
```bash
docker compose ps
docker compose logs -f app
curl -i http://127.0.0.1:8080/health
```

### 6) 운영 배포(업데이트)
```bash
cd /opt/soo-auth-service
git pull
docker compose up -d --build
docker compose ps
```

### 7) 재시작/중지
```bash
docker compose restart app
docker compose stop
docker compose start
```

### 8) 완전 종료(주의)
```bash
# 컨테이너만 내림 (데이터 유지)
docker compose down

# 컨테이너 + 볼륨 삭제 (DB/Redis 데이터 삭제)
docker compose down -v
```

## 로컬 개발 실행
```bash
./gradlew :core:core-api:bootRun
```

## 문서 재생성
```bash
./gradlew :core:core-api:generateApiDocs
```

## 자동화 배포 요약

권장 방식:
1. `main` 머지 시 GitHub Actions가 Docker 이미지를 빌드/푸시
2. Actions가 배포 서버에 SSH 접속
3. 서버에서 `docker compose pull && docker compose up -d` 실행

상세 절차/시크릿/워크플로 파일 예시는 아래 문서를 참고하세요.
- [docs/README_DEPLOY_AUTOMATION.md](docs/README_DEPLOY_AUTOMATION.md)
