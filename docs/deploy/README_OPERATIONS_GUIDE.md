# soo-auth-service 운영 가이드 (Docker Hub 기반)

이 문서는 Docker Hub에 이미지를 릴리즈하고, 이를 기반으로 **운영 서버에서 소스 코드 없이** 시스템을 구축 및 운영하는 상세 방법을 설명합니다.

---

## 🚀 1. 빠른 시작 (운영 서버 - 소스 코드 없이 실행)

운영 서버에서는 프로젝트의 전체 소스 코드가 필요하지 않습니다. **오케스트레이션 파일(Docker Compose, Nginx 설정)**만 있으면 Docker Hub에서 이미지를 내려받아 즉시 실행할 수 있습니다.

### 1.1 필수 파일 가져오기 (Git Sparse Checkout)
아래 명령어를 복사하여 실행하면, 소스 코드를 제외한 **배포 관련 파일만** 선별적으로 가져옵니다.

```bash
# 1. 작업 디렉토리 생성
mkdir soo-auth && cd soo-auth

# 2. Git 설정 및 필요한 폴더만 가져오기 (Sparse Checkout)
git init
git remote add origin https://github.com/your-repo/soo-auth-service.git # 실제 저장소 주소로 변경
git config core.sparseCheckout true
echo "deploy/*" >> .git/info/sparse-checkout
echo "nginx/*" >> .git/info/sparse-checkout
echo "docker-compose.yml" >> .git/info/sparse-checkout
git pull --depth 1 origin main

# 3. 환경 변수 설정
cp .env.example .env  # .env 파일이 없다면 생성 후 편집
nano .env             # DOCKER_HUB_USERNAME 및 각종 비밀번호 설정
```

### 1.2 즉시 실행
```bash
# 실행 권한 부여
chmod +x deploy/deploy.sh

# 원하는 서비스 조합으로 실행
# 예: 코어 인프라 + 웹 + 공통인증 + OAuth2 + 관리자
./deploy/deploy.sh --core --web --common --oauth2 --admin
```

---

## 2. 시스템 아키텍처 (운영 모델)

- **단일 이미지**: `soo-auth-service` 이미지는 모든 인증 모듈(`email`, `sms`, `id`, `oauth2`, `admin`)을 포함합니다.
- **다중 컨테이너**: Docker Compose를 통해 동일한 이미지를 각각 다른 환경 변수와 함께 실행하여 독립적인 프로세스로 운영합니다.
- **라우팅**: `Nginx`가 진입점에서 요청 경로(`/v1/auth/email`, `/v1/admin` 등)를 분석하여 해당 컨테이너로 전달합니다.
- **자원 최적화**: 사용하지 않는 인증 방식의 컨테이너는 실행하지 않음으로써 운영 서버의 메모리 및 CPU 자원을 절약할 수 있습니다.

---

## 3. 이미지 릴리즈 가이드 (개발/CI 환경)

운영 서버에 반영하기 위해 개발 환경(PC 또는 CI)에서 이미지를 빌드하여 Docker Hub에 푸시하는 과정입니다.

### 3.1 이미지 빌드 및 푸시
```bash
# 1. Docker Hub 로그인
docker login -u <your-username>

# 2. 이미지 빌드
docker build -t $DOCKER_HUB_USERNAME/soo-auth-service:latest .

# 3. 이미지 푸시
docker push $DOCKER_HUB_USERNAME/soo-auth-service:latest
```

---

## 4. 운영 서버 배포 상세 (소스 코드 미포함)

### 4.1 디렉토리 구조
운영 서버에는 아래와 같은 최소한의 파일 구성이 필요합니다.

```text
soo-auth/
├── .env                 # 운영 환경 설정 (필수)
├── docker-compose.yml   # 메인 오케스트레이션 파일
├── deploy/              # 서비스 정의 및 배포 스크립트
│   ├── deploy.sh        # 배포 헬퍼 스크립트
│   ├── docker-compose.app-base.yml
│   └── docker-compose.auth-*.yml
└── nginx/               # Nginx 라우팅 설정
    ├── nginx.conf
    └── conf.d/default.conf
```

### 4.2 파일 전송 방법 (Git이 없는 경우)
만약 운영 서버에 Git이 설치되어 있지 않다면, 개발 환경에서 아래 명령어로 배포 아카이브를 만들어 전송하세요.

```bash
# 개발 환경(PC)에서 실행
tar -czvf soo-auth-deploy.tar.gz docker-compose.yml deploy/ nginx/ .env.example

# 운영 서버로 전송
scp soo-auth-deploy.tar.gz user@server-ip:~/
```

### 4.3 업데이트 및 유지보수
새로운 이미지가 Docker Hub에 올라왔을 때 시스템을 업데이트하는 방법입니다.

```bash
# deploy.sh를 사용하면 pull과 restart를 한 번에 처리합니다.
./deploy/deploy.sh --oauth2 --admin # 기존에 실행하던 옵션 그대로 실행
```

---

## 5. 상세 운영 가이드

운영 요구사항에 따라 특정 인증 방식을 활성화하거나 비활성화하여 자원을 최적화할 수 있습니다.

### 5.1 인증 방식별 프로파일 조합
각 서비스는 `docker compose --profile <name>` 명령으로 제어됩니다.

| 기능 | 프로파일 이름 | 컨테이너 이름 | 설명 |
| :--- | :--- | :--- | :--- |
| **인프라** | `core` | `mariadb`, `redis` | 데이터 저장소 (필수) |
| **웹/라우팅** | `web` | `nginx`, `frontend` | 진입점 및 관리자 UI (필수) |
| **공통 인증** | `auth-common` | `app-common` | 토큰 발급 및 세션 관리 (필수) |
| **이메일** | `auth-email` | `app-email` | 이메일 기반 회원가입/로그인 |
| **ID/PW** | `auth-id` | `app-id` | 아이디 기반 회원가입/로그인 |
| **SMS** | `auth-sms` | `app-sms` | 휴대폰 인증 및 SMS 로그인 |
| **OAuth2** | `auth-oauth2` | `app-oauth2` | 소셜 로그인 처리 |
| **관리자** | `admin` | `app-admin` | 관리자 API 및 사용자 관리 |

### 5.2 자원 최적화 전략
불필요한 컨테이너를 실행하지 않으려면 `deploy.sh`에서 해당 플래그를 제외하거나, `docker compose` 명령 시 해당 프로파일을 호출하지 않으면 됩니다.

**예: 아이디 로그인과 관리자 기능만 사용하는 경우**
```bash
./deploy/deploy.sh --core --web --common --id --admin
```
이 경우 `app-email`, `app-sms`, `app-oauth2` 컨테이너는 생성되지 않으며, 관련 자원 소모가 발생하지 않습니다.

### 5.3 시나리오별 즉시 실행 명령어 (복사/붙여넣기)

특정 인증 방식 하나와 관리자 기능을 함께 사용하는 가장 일반적인 시나리오들입니다.

| 시나리오 | 실행 명령어 (추천: deploy.sh) |
| :--- | :--- |
| **이메일 로그인 전용** | `./deploy/deploy.sh --core --web --common --email --admin` |
| **ID/PW 로그인 전용** | `./deploy/deploy.sh --core --web --common --id --admin` |
| **SMS 로그인 전용** | `./deploy/deploy.sh --core --web --common --sms --admin` |
| **OAuth2 로그인 전용** | `./deploy/deploy.sh --core --web --common --oauth2 --admin` |
| **모든 기능 활성화** | `./deploy/deploy.sh --all` |
| **관리 도구(phpMyAdmin) 포함** | `./deploy/deploy.sh --core --web --common --oauth2 --admin --tools` |

> **Tip**: 만약 관리자(Admin) 기능이 필요 없다면 위 명령어에서 `--admin` 플래그만 제외하면 됩니다.
> **Note**: phpMyAdmin은 `--tools` 프로파일에 포함되어 있으며, 실행 시 `/phpmyadmin` 경로로 접속 가능합니다.

#### Docker Compose 직접 사용 시
스크립트 없이 `docker compose` 명령어를 직접 사용하려는 경우 아래와 같이 입력하세요.

```bash
# 예: OAuth2 전용 실행
docker compose \
  --profile core \
  --profile web \
  --profile auth-common \
  --profile auth-oauth2 \
  --profile admin \
  up -d
```

---

## 6. 환경 변수(.env) 설정 상세

운영 서버의 `.env` 파일에서 주요 설정값을 조정합니다.

### 6.1 인증 방식 활성화 (Runtime Toggle)
컨테이너가 실행되더라도 애플리케이션 내부에서 빈(Bean) 로딩을 제어합니다. (보안 및 메모리 절약)
- `AUTH_EMAIL_ENABLED=true/false`
- `AUTH_ID_ENABLED=true/false`
- `AUTH_SMS_ENABLED=true/false`
- `AUTH_OAUTH2_ENABLED=true/false`
- `AUTH_ADMIN_ENABLED=true/false`

### 6.2 보안 및 인프라
- `SPRING_PROFILES_ACTIVE=live`: 운영 환경 프로파일 적용.
- `JWT_KEYSTORE_PASSWORD`: 토큰 서명을 위한 키스토어 암호.
- `MARIADB_PASSWORD`: DB 접속 비밀번호.

---

## 7. 유지보수 및 업데이트

1. **이미지 업데이트**: Docker Hub에 새 이미지가 푸시되면 운영 서버에서 `docker compose pull` 후 `deploy.sh`를 재실행합니다.
2. **로그 확인**: `docker compose logs -f <service-name>` 명령으로 특정 컨테이너의 로그를 모니터링할 수 있습니다.
3. **자원 모니터링**: `docker stats` 명령을 통해 각 컨테이너별 실제 자원 사용량을 확인할 수 있습니다.

---

## 8. 트러블슈팅 (Apple Silicon / M1, M2, M3 사용자)

Apple Silicon(ARM64) 기반 Mac 환경에서 실행 시 `no matching manifest for linux/arm64/v8` 에러가 발생할 수 있습니다.

- **원인**: Docker Hub에 업로드된 이미지가 Intel(AMD64) 아키텍처용으로만 빌드된 경우 발생합니다.
- **해결**: `docker-compose.yml`에 `platform: linux/amd64` 설정을 추가하여 Rosetta 2를 통해 실행하도록 구성했습니다. (현재 프로젝트 설정에 이미 반영됨)
- 만약 직접 빌드하여 사용하신다면, 아래 명령어로 본인 아키텍처에 맞게 다시 빌드하세요:
  ```bash
  docker build --platform linux/arm64 -t $DOCKER_HUB_USERNAME/soo-auth-service:latest .
  ```

---
